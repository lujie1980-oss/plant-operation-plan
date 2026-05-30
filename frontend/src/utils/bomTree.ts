import type { BomMd, MaterialMd, ProductResourceMd } from '../types/masterData';

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
