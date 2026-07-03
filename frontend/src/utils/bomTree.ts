import type { BomMd, MaterialMd, ProductResourceMd } from '../types/masterData';
import type { MaterialCatalog } from './materialCatalog';

export interface BomTreeNode {
  productCode: string;
  productName: string | null;
  materialType: string | null;
  uomCode: string | null;
  siteCode: string | null;
  qty: number;
  isCritical: boolean;
  scrapRate: number | null;
  bomId: string | null;
  bomVersion: string | null;
  bomEffectiveFrom: string | null;
  bomEffectiveTo: string | null;
  componentEffectiveFrom: string | null;
  componentEffectiveTo: string | null;
  children: BomTreeNode[];
}

export interface FinishedProductRow {
  materialCode: string;
  materialName: string | null;
  materialType: string | null;
  siteCode: string | null;
  uomCode: string | null;
  hasBom: boolean;
  hasRouting: boolean;
}

/** Mirrors backend BomComponentEntity.findChildren */
export function findBomChildren(
  boms: BomMd[],
  finishedProductCode: string,
  parentProductCode: string,
): BomMd[] {
  const scoped = boms.filter(
    (b) =>
      b.finishedProductCode === finishedProductCode && b.parentProductCode === parentProductCode,
  );
  if (scoped.length > 0) {
    return scoped;
  }
  return boms.filter((b) => b.parentProductCode === parentProductCode);
}

export function listFinishedProducts(
  boms: BomMd[],
  materials: MaterialMd[],
  productResources: ProductResourceMd[],
): FinishedProductRow[] {
  const finishedCodes = new Set<string>();

  for (const bom of boms) {
    const fc = bom.finishedProductCode?.trim();
    if (fc) {
      finishedCodes.add(fc);
    }
  }

  for (const pr of productResources) {
    const level = pr.bomLevel?.trim() ?? '';
    if (level.includes('成品')) {
      finishedCodes.add(pr.productCode);
    }
  }

  for (const mat of materials) {
    if (isFinishedMaterialType(mat.materialType) && !isSemiFinishedMaterialType(mat.materialType)) {
      finishedCodes.add(mat.materialCode);
    }
  }

  const componentCodes = new Set(
    boms.map((b) => b.componentProductCode?.trim()).filter((c): c is string => Boolean(c)),
  );
  const routedCodes = new Set(productResources.map((r) => r.productCode));
  const materialByCode = new Map(materials.map((m) => [m.materialCode, m]));

  return [...finishedCodes]
    .filter((materialCode) => {
      const mat = materialByCode.get(materialCode);
      if (isSemiFinishedMaterialType(mat?.materialType)) {
        return false;
      }
      const isBomFinished = boms.some((b) => b.finishedProductCode === materialCode);
      if (isBomFinished) {
        return true;
      }
      if (isFinishedMaterialType(mat?.materialType)) {
        return true;
      }
      if (productResources.some((r) => r.productCode === materialCode && r.bomLevel?.includes('成品'))) {
        return true;
      }
      // 仅作为他人 BOM 子件出现的料号视为半成品，不展示
      if (componentCodes.has(materialCode)) {
        return false;
      }
      return false;
    })
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    .map((materialCode) => {
      const mat = materialByCode.get(materialCode);
      const hasBom =
        boms.some((b) => b.finishedProductCode === materialCode) ||
        findBomChildren(boms, materialCode, materialCode).length > 0;
      return {
        materialCode,
        materialName: mat?.materialName ?? null,
        materialType: mat?.materialType ?? null,
        siteCode: mat?.siteCode ?? null,
        uomCode: mat?.uomCode ?? null,
        hasBom,
        hasRouting: routedCodes.has(materialCode),
      };
    });
}

function isFinishedMaterialType(materialType: string | null | undefined): boolean {
  if (!materialType) {
    return false;
  }
  const t = materialType.trim().toLowerCase();
  return t.includes('成品') || t === 'fg' || t === 'finished';
}

function isSemiFinishedMaterialType(materialType: string | null | undefined): boolean {
  if (!materialType) {
    return false;
  }
  const t = materialType.trim().toLowerCase();
  return t.includes('半成品') || t.includes('semi') || t === 'wip';
}

export interface BomDetailListRow {
  level: number;
  levelLabel: string;
  parentProductCode: string;
  parentMaterialNo: string;
  componentProductCode: string;
  componentMaterialNo: string;
  componentQty: number;
  componentSpecification: string;
  finishedProductCode: string;
}

/** 按 "/" 拆分料号：首段为物料号，其余段合并为规格。 */
export function splitProductCodeBySlash(code: string): {
  materialNo: string;
  specification: string;
} {
  const trimmed = (code ?? '').trim();
  if (!trimmed) {
    return { materialNo: '—', specification: '—' };
  }
  const segments = trimmed.split('/').map((s) => s.trim()).filter(Boolean);
  if (segments.length <= 1) {
    return { materialNo: segments[0] ?? trimmed, specification: '—' };
  }
  return {
    materialNo: segments[0],
    specification: segments.slice(1).join(' / '),
  };
}

function bomDetailLevelLabel(depth: number): string {
  if (depth === 1) return '一阶';
  if (depth === 2) return '二阶';
  if (depth === 3) return '三阶';
  return `${depth}阶`;
}

export function flattenBomDetailRows(boms: BomMd[], finishedProductCode: string): BomDetailListRow[] {
  const finished = finishedProductCode?.trim();
  if (!finished) {
    return [];
  }
  const rows: BomDetailListRow[] = [];
  const visited = new Set<string>();

  const walk = (parentCode: string, depth: number) => {
    const visitKey = `${finished}|${parentCode}`;
    if (visited.has(visitKey)) {
      return;
    }
    visited.add(visitKey);

    for (const bom of findBomChildren(boms, finished, parentCode)) {
      const parentSplit = splitProductCodeBySlash(parentCode);
      const componentSplit = splitProductCodeBySlash(bom.componentProductCode);
      rows.push({
        level: depth,
        levelLabel: bomDetailLevelLabel(depth),
        parentProductCode: parentCode,
        parentMaterialNo: parentSplit.materialNo,
        componentProductCode: bom.componentProductCode,
        componentMaterialNo: componentSplit.materialNo,
        componentQty: bom.componentQty,
        componentSpecification: componentSplit.specification,
        finishedProductCode: finished,
      });
      walk(bom.componentProductCode, depth + 1);
    }
  };

  walk(finished, 1);
  return rows;
}

export interface BomHierarchyListRow {
  level: number;
  parentProductCode: string;
  parentMaterialName: string;
  componentProductCode: string;
  componentMaterialName: string;
  componentQty: number;
  finishedProductCode: string;
}

export interface BomDisplayRow {
  level: number;
  parentProductCode: string;
  parentMaterialId: number | null;
  parentMaterialName: string;
  componentProductCode: string;
  componentMaterialId: number | null;
  componentMaterialName: string;
  finishedProductCode: string;
}

export function filterBomsToMaterialMaster(boms: BomMd[], catalog: MaterialCatalog): BomMd[] {
  return boms.filter(
    (b) => catalog.has(b.parentProductCode) && catalog.has(b.componentProductCode),
  );
}

export function toBomDisplayRows(rows: BomDetailListRow[], catalog: MaterialCatalog): BomDisplayRow[] {
  return rows.map((row) => ({
    level: row.level,
    parentProductCode: row.parentProductCode,
    parentMaterialId: catalog.materialId(row.parentProductCode),
    parentMaterialName: catalog.materialName(row.parentProductCode),
    componentProductCode: row.componentProductCode,
    componentMaterialId: catalog.materialId(row.componentProductCode),
    componentMaterialName: catalog.materialName(row.componentProductCode),
    finishedProductCode: row.finishedProductCode,
  }));
}

export function flattenBomDisplayRows(
  boms: BomMd[],
  finishedProductCodes: string[],
  catalog: MaterialCatalog,
): BomDisplayRow[] {
  const filtered = filterBomsToMaterialMaster(boms, catalog);
  const flat = flattenAllFinishedBomRows(filtered, finishedProductCodes);
  return toBomDisplayRows(flat, catalog);
}

export function flattenAllFinishedBomRows(
  boms: BomMd[],
  finishedProductCodes: string[],
): BomDetailListRow[] {
  const rows: BomDetailListRow[] = [];
  const seen = new Set<string>();
  for (const finished of finishedProductCodes) {
    for (const row of flattenBomDetailRows(boms, finished)) {
      const key = `${row.parentProductCode}|${row.componentProductCode}|${row.level}`;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      rows.push(row);
    }
  }
  return rows.sort((a, b) => {
    if (a.level !== b.level) {
      return a.level - b.level;
    }
    const byParent = a.parentProductCode.localeCompare(b.parentProductCode, 'zh-CN');
    if (byParent !== 0) {
      return byParent;
    }
    return a.componentProductCode.localeCompare(b.componentProductCode, 'zh-CN');
  });
}

export function flattenBomHierarchyRows(
  boms: BomMd[],
  finishedProductCode: string,
  materialNameByCode: Map<string, string>,
): BomHierarchyListRow[] {
  return flattenBomDetailRows(boms, finishedProductCode).map((row) => ({
    level: row.level,
    parentProductCode: row.parentProductCode,
    parentMaterialName:
      materialNameByCode.get(row.parentProductCode) ?? row.parentProductCode,
    componentProductCode: row.componentProductCode,
    componentMaterialName:
      materialNameByCode.get(row.componentProductCode) ?? row.componentProductCode,
    componentQty: row.componentQty,
    finishedProductCode: row.finishedProductCode,
  }));
}

/** 收集 BOM 展开涉及的全部料号（含成品根节点）。 */
export function collectBomMaterialCodes(boms: BomMd[], finishedProductCode: string): Set<string> {
  const codes = new Set<string>();
  const finished = finishedProductCode?.trim();
  if (!finished) {
    return codes;
  }
  codes.add(finished);
  for (const row of flattenBomDetailRows(boms, finished)) {
    codes.add(row.parentProductCode);
    codes.add(row.componentProductCode);
  }
  return codes;
}

export function collectAllBomMaterialCodes(boms: BomMd[], finishedProductCodes: string[]): Set<string> {
  const codes = new Set<string>();
  for (const finished of finishedProductCodes) {
    for (const code of collectBomMaterialCodes(boms, finished)) {
      codes.add(code);
    }
  }
  return codes;
}

export function buildFinishedProductBomRoot(
  finishedProductCode: string,
  boms: BomMd[],
  materials: MaterialMd[],
): BomTreeNode | null {
  const finished = finishedProductCode?.trim();
  if (!finished) {
    return null;
  }
  const mat = materials.find((m) => m.materialCode === finished);
  return {
    productCode: finished,
    productName: mat?.materialName ?? null,
    materialType: mat?.materialType ?? null,
    uomCode: mat?.uomCode ?? null,
    siteCode: mat?.siteCode ?? null,
    qty: 1,
    isCritical: true,
    scrapRate: null,
    bomId: null,
    bomVersion: null,
    bomEffectiveFrom: null,
    bomEffectiveTo: null,
    componentEffectiveFrom: null,
    componentEffectiveTo: null,
    children: buildBomTree(boms, materials, finished, finished),
  };
}

export function buildBomTree(
  boms: BomMd[],
  materials: MaterialMd[],
  finishedProductCode: string,
  parentProductCode: string,
  visited: Set<string> = new Set(),
): BomTreeNode[] {
  if (visited.has(`${finishedProductCode}|${parentProductCode}`)) {
    return [];
  }
  visited.add(`${finishedProductCode}|${parentProductCode}`);

  const materialByCode = new Map(materials.map((m) => [m.materialCode, m]));
  const children = findBomChildren(boms, finishedProductCode, parentProductCode);

  return children.map((bom) => {
    const productCode = bom.componentProductCode;
    const nested = buildBomTree(boms, materials, finishedProductCode, productCode, visited);
    const mat = materialByCode.get(productCode);
    return {
      productCode,
      productName: mat?.materialName ?? null,
      materialType: mat?.materialType ?? null,
      uomCode: mat?.uomCode ?? null,
      siteCode: mat?.siteCode ?? null,
      qty: bom.componentQty,
      isCritical: bom.isCriticalComponent,
      scrapRate: bom.scrapRate,
      bomId: bom.bomId,
      bomVersion: bom.bomVersion,
      bomEffectiveFrom: bom.bomEffectiveFrom,
      bomEffectiveTo: bom.bomEffectiveTo,
      componentEffectiveFrom: bom.componentEffectiveFrom,
      componentEffectiveTo: bom.componentEffectiveTo,
      children: nested,
    };
  });
}

export function collectBomTreeProductCodes(nodes: BomTreeNode[]): string[] {
  const codes: string[] = [];
  const walk = (list: BomTreeNode[]) => {
    for (const node of list) {
      codes.push(node.productCode);
      walk(node.children);
    }
  };
  walk(nodes);
  return codes;
}
