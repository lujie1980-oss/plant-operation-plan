import type { BomTreeNode } from './bomTree';
import type { ProductResourceMd, ProductionLineMd, ResourceMd } from '../types/masterData';

export interface MfgFlowMaterial {
  id: string;
  productCode: string;
  materialName: string | null;
  tooltip: string;
  kind: 'raw' | 'semi' | 'finished';
}

export interface MfgFlowOperation {
  id: string;
  /** 工序名称（节点展示） */
  operationName: string;
  tooltip: string;
  sequenceNo: number;
}

/** 一个 BOM 层级的制造子树：子件在下方，工序在中层，本层物料在上方 */
export interface MfgFlowBomStage {
  stageId: string;
  bomLevel: number;
  productCode: string;
  productLabel: string;
  material: MfgFlowMaterial;
  operations: MfgFlowOperation[];
  /** BOM 子件（各自为独立子树） */
  children: MfgFlowBomStage[];
  /** 叶节点原料（无下级 BOM） */
  leafInputs: MfgFlowMaterial[];
}

function routingForProduct(
  productResources: ProductResourceMd[],
  productCode: string,
): ProductResourceMd[] {
  return productResources
    .filter((r) => r.productCode === productCode)
    .sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0));
}

function productLabel(code: string, name: string | null): string {
  return name ? `${code} · ${name}` : code;
}

function materialTooltip(
  productCode: string,
  materialName: string | null,
  kind: MfgFlowMaterial['kind'],
  extra?: string,
): string {
  const kindLabel = kind === 'finished' ? '成品' : kind === 'semi' ? '半成品' : '物料';
  return [productCode, materialName, kindLabel, extra].filter(Boolean).join(' · ');
}

function buildResourceIndex(resources: ResourceMd[]): Map<string, ResourceMd> {
  return new Map(resources.map((r) => [r.resourceId, r]));
}

function buildLineIndex(lines: ProductionLineMd[]): Map<string, ProductionLineMd> {
  return new Map(lines.map((line) => [line.lineId, line]));
}

/** 工艺路径 resourceId 可能是产线 ID 或资源 ID；展示用资源名称（设备组），产线仅放 tooltip */
function resolveResourceDisplay(
  step: ProductResourceMd,
  resourceById: Map<string, ResourceMd>,
  lineByLineId: Map<string, ProductionLineMd>,
): { resourceName: string; lineId: string | null } {
  const line = lineByLineId.get(step.resourceId);
  if (line) {
    const resource = resourceById.get(line.resourceId);
    return {
      resourceName: resource?.resourceId ?? line.resourceId,
      lineId: line.lineId,
    };
  }
  const resource = resourceById.get(step.resourceId);
  return {
    resourceName: resource?.resourceId ?? step.resourceId,
    lineId: null,
  };
}

function buildOperations(
  productCode: string,
  productResources: ProductResourceMd[],
  resourceById: Map<string, ResourceMd>,
  lineByLineId: Map<string, ProductionLineMd>,
): MfgFlowOperation[] {
  return routingForProduct(productResources, productCode).map((step) => {
    const { resourceName, lineId } = resolveResourceDisplay(step, resourceById, lineByLineId);
    const seq = step.sequenceNo ?? 0;
    const opName = step.operationName?.trim() || `工序 ${seq || '—'}`;
    const tooltipParts = [
      `序号 ${seq || '—'}`,
      `工序 ${opName}`,
      `资源 ${resourceName}`,
      lineId ? `产线 ${lineId}` : null,
      step.processTimeSeconds != null ? `CT ${step.processTimeSeconds}s` : null,
      step.setupTimeMinutes != null ? `换型 ${step.setupTimeMinutes}min` : null,
      step.bomLevel ? `阶层 ${step.bomLevel}` : null,
    ].filter(Boolean);

    return {
      id: `op:${productCode}:${step.sequenceNo ?? step.resourceId}`,
      operationName: opName,
      tooltip: tooltipParts.join(' · '),
      sequenceNo: seq,
    };
  });
}

function buildStage(
  node: BomTreeNode,
  bomLevel: number,
  finishedCode: string,
  productResources: ProductResourceMd[],
  resourceById: Map<string, ResourceMd>,
  lineByLineId: Map<string, ProductionLineMd>,
  isRoot: boolean,
): MfgFlowBomStage {
  const kind: MfgFlowMaterial['kind'] = isRoot ? 'finished' : 'semi';
  const label = productLabel(node.productCode, node.productName);

  const childStages: MfgFlowBomStage[] = [];
  const leafInputs: MfgFlowMaterial[] = [];

  for (const child of node.children) {
    if (child.children.length === 0) {
      leafInputs.push({
        id: `leaf:${child.productCode}`,
        productCode: child.productCode,
        materialName: child.productName,
        tooltip: materialTooltip(
          child.productCode,
          child.productName,
          'raw',
          child.isCritical ? `关键件 ×${child.qty}` : `组件 ×${child.qty}`,
        ),
        kind: 'raw',
      });
    } else {
      childStages.push(
        buildStage(child, bomLevel + 1, finishedCode, productResources, resourceById, lineByLineId, false),
      );
    }
  }

  return {
    stageId: `stage:${node.productCode}:${bomLevel}`,
    bomLevel,
    productCode: node.productCode,
    productLabel: label,
    material: {
      id: `mat:${node.productCode}`,
      productCode: node.productCode,
      materialName: node.productName,
      tooltip: materialTooltip(node.productCode, node.productName, kind, isRoot ? '成品' : '半成品'),
      kind,
    },
    operations: buildOperations(node.productCode, productResources, resourceById, lineByLineId),
    children: childStages,
    leafInputs,
  };
}

export function buildManufacturingFlowTree(
  finishedProductCode: string,
  finishedProductName: string | null,
  bomRoots: BomTreeNode[],
  productResources: ProductResourceMd[],
  resources: ResourceMd[],
  lines: ProductionLineMd[],
): MfgFlowBomStage | null {
  const resourceById = buildResourceIndex(resources);
  const lineByLineId = buildLineIndex(lines);

  const rootNode: BomTreeNode = {
    productCode: finishedProductCode,
    productName: finishedProductName,
    materialType: null,
    uomCode: null,
    siteCode: null,
    qty: 1,
    isCritical: true,
    scrapRate: null,
    bomId: null,
    bomVersion: null,
    bomEffectiveFrom: null,
    bomEffectiveTo: null,
    componentEffectiveFrom: null,
    componentEffectiveTo: null,
    children: bomRoots,
  };

  if (bomRoots.length === 0) {
    const label = productLabel(finishedProductCode, finishedProductName);
    const operations = buildOperations(
      finishedProductCode,
      productResources,
      resourceById,
      lineByLineId,
    );
    if (operations.length === 0) {
      return null;
    }
    return {
      stageId: `stage:${finishedProductCode}:0`,
      bomLevel: 0,
      productCode: finishedProductCode,
      productLabel: label,
      material: {
        id: `mat:${finishedProductCode}`,
        productCode: finishedProductCode,
        materialName: finishedProductName,
        tooltip: materialTooltip(finishedProductCode, finishedProductName, 'finished', '成品'),
        kind: 'finished',
      },
      operations,
      children: [],
      leafInputs: [],
    };
  }

  return buildStage(rootNode, 0, finishedProductCode, productResources, resourceById, lineByLineId, true);
}

/** @deprecated 保留类型兼容，请使用 buildManufacturingFlowTree */
export type ManufacturingFlowNodeType =
  | 'RAW_MATERIAL'
  | 'SEMI_FINISHED'
  | 'OPERATION'
  | 'FINISHED';

export interface ManufacturingFlowNode {
  nodeId: string;
  nodeType: ManufacturingFlowNodeType;
  label: string;
  subtitle?: string;
  productCode?: string;
  level: number;
  groupLabel?: string;
}

export interface ManufacturingFlowEdge {
  fromNodeId: string;
  toNodeId: string;
}

export interface ManufacturingFlowGraph {
  nodes: ManufacturingFlowNode[];
  edges: ManufacturingFlowEdge[];
}

export function buildManufacturingFlowGraph(
  finishedProductCode: string,
  finishedProductName: string | null,
  bomRoots: BomTreeNode[],
  productResources: ProductResourceMd[],
  resources: ResourceMd[],
  lines: ProductionLineMd[],
): ManufacturingFlowGraph {
  const tree = buildManufacturingFlowTree(
    finishedProductCode,
    finishedProductName,
    bomRoots,
    productResources,
    resources,
    lines,
  );
  if (!tree) {
    return { nodes: [], edges: [] };
  }
  return { nodes: [{ nodeId: tree.stageId, nodeType: 'FINISHED', label: tree.productLabel, level: 0 }], edges: [] };
}
