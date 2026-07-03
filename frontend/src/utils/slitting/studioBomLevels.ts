import type { BomMd } from '../../types/masterData';
import type { ChildSlittingOrder, MasterRoll } from '../../types/slitting';
import { bomMaterialRollCode } from './virtualMasterFromBom';
import { findBomChildren, flattenBomDetailRows } from '../bomTree';
import type { MaterialCatalog } from '../materialCatalog';

export type BomLevel = number;

export type SourceMode = 'inventory' | 'bom';

export type StudioSourceSelection =
  | { kind: 'roll'; rollCode: string; productCode: string }
  | { kind: 'bom'; productCode: string };

const PRIMARY_FINISHED = 'M69/305*600M/1R/深黄';

export function resolvePrimaryFinished(boms: BomMd[]): string {
  const codes = new Set(
    boms.map((b) => b.finishedProductCode).filter((c): c is string => Boolean(c?.trim())),
  );
  if (codes.has(PRIMARY_FINISHED)) {
    return PRIMARY_FINISHED;
  }
  return [...codes][0] ?? '';
}

export function bomLevelChineseLabel(level: number): string {
  if (level === 1) return '一级';
  if (level === 2) return '二级';
  if (level === 3) return '三级';
  if (level === 4) return '四级';
  if (level === 5) return '五级';
  return `${level}级`;
}

/** BOM 最大层级（至少为 1） */
export function maxBomDepth(boms: BomMd[], finishedProductCode: string): number {
  const finished = finishedProductCode?.trim();
  if (!finished) {
    return 1;
  }
  const rows = flattenBomDetailRows(boms, finished);
  if (rows.length === 0) {
    return 1;
  }
  return Math.max(1, ...rows.map((r) => r.level));
}

export function bomLevelOptions(maxDepth: number): number[] {
  const depth = Math.max(1, maxDepth);
  return Array.from({ length: depth }, (_, i) => i + 1);
}

/** 指定 BOM 层级（1=成品下子件，2=再下一层，依此类推）的物料料号 */
export function bomMaterialCodesAtLevel(
  boms: BomMd[],
  finishedProductCode: string,
  level: number,
  catalog: MaterialCatalog,
): string[] {
  const finished = finishedProductCode?.trim();
  if (!finished || level < 1) {
    return [];
  }
  if (level === 1) {
    return findBomChildren(boms, finished, finished)
      .map((b) => b.componentProductCode)
      .filter((code) => catalog.has(code));
  }
  const parentCodes = bomMaterialCodesAtLevel(boms, finished, level - 1, catalog);
  const out = new Set<string>();
  for (const parent of parentCodes) {
    for (const child of findBomChildren(boms, finished, parent)) {
      if (catalog.has(child.componentProductCode)) {
        out.add(child.componentProductCode);
      }
    }
  }
  return [...out].sort((a, b) => a.localeCompare(b, 'zh-CN'));
}

/** 各 BOM 层级物料料号合集（用于母卷/BOM 来源解析） */
export function allBomMaterialCodes(
  boms: BomMd[],
  finishedProductCode: string,
  catalog: MaterialCatalog,
): string[] {
  const max = maxBomDepth(boms, finishedProductCode);
  const codes = new Set<string>();
  for (let level = 1; level <= max; level++) {
    for (const code of bomMaterialCodesAtLevel(boms, finishedProductCode, level, catalog)) {
      codes.add(code);
    }
  }
  return [...codes];
}

export function inventoryRollsAtLevel(
  allRolls: MasterRoll[],
  boms: BomMd[],
  finishedProductCode: string,
  level: number,
  catalog: MaterialCatalog,
): MasterRoll[] {
  const finished = finishedProductCode?.trim();
  if (!finished) {
    return allRolls;
  }
  if (level === 1) {
    return allRolls.filter((roll) => {
      const code = roll.productCode ?? roll.finishedProductCode ?? roll.materialCode ?? '';
      return code === finished || roll.finishedProductCode === finished;
    });
  }
  const parentLevelCodes = new Set(bomMaterialCodesAtLevel(boms, finished, level - 1, catalog));
  return allRolls.filter((roll) => {
    const code = roll.productCode ?? roll.materialCode ?? '';
    return parentLevelCodes.has(code);
  });
}

export function findBomParentCode(
  boms: BomMd[],
  finishedProductCode: string,
  componentCode: string,
): string | null {
  const finished = finishedProductCode?.trim();
  if (!finished || !componentCode) {
    return null;
  }
  const walk = (parentCode: string): string | null => {
    for (const row of findBomChildren(boms, finished, parentCode)) {
      if (row.componentProductCode === componentCode) {
        return parentCode;
      }
      const nested = walk(row.componentProductCode);
      if (nested) {
        return nested;
      }
    }
    return null;
  };
  if (findBomChildren(boms, finished, finished).some((b) => b.componentProductCode === componentCode)) {
    return finished;
  }
  for (const row of findBomChildren(boms, finished, finished)) {
    const p = walk(row.componentProductCode);
    if (p) {
      return p;
    }
  }
  return null;
}

/** 从分切树母卷节点解析来源（库存卷或 BOM 虚拟卷） */
export function resolveSourceForMasterNode(
  masterNodeId: string,
  allMasters: MasterRoll[],
  bomProductCodes: string[],
): StudioSourceSelection | null {
  const rollCode = masterNodeId.replace(/^MASTER-/, '');
  const inventory = allMasters.find((m) => m.rollCode === rollCode);
  if (inventory) {
    return {
      kind: 'roll',
      rollCode: inventory.rollCode,
      productCode:
        inventory.productCode ?? inventory.finishedProductCode ?? inventory.materialCode ?? '',
    };
  }
  const bomCode = bomProductCodes.find((code) => bomMaterialRollCode(code) === rollCode);
  if (bomCode) {
    return { kind: 'bom', productCode: bomCode };
  }
  return null;
}

/** 可由当前卷/BOM 物料分切的需求：当前物料对应子订单（上一层为父件时的产出需求） */
export function slittableDemandsForSource(
  source: StudioSourceSelection,
  boms: BomMd[],
  finishedProductCode: string,
  orders: ChildSlittingOrder[],
): ChildSlittingOrder[] {
  const finished = finishedProductCode?.trim();
  if (!finished) {
    return [];
  }

  let targetProductCode: string;
  if (source.kind === 'roll') {
    const children = findBomChildren(boms, finished, source.productCode).map((b) => b.componentProductCode);
    if (children.length === 0) {
      targetProductCode = source.productCode;
    } else {
      return orders.filter((o) => children.includes(o.productCode ?? ''));
    }
  } else {
    targetProductCode = source.productCode;
  }

  return orders.filter((o) => (o.productCode ?? '') === targetProductCode);
}

/** 子订单尺寸是否可放入母卷（允许旋转 90°）。 */
export function orderFitsMasterRoll(
  order: Pick<ChildSlittingOrder, 'widthMm' | 'lengthMm'>,
  masterWidthMm: number,
  masterLengthMm: number,
): boolean {
  const ow = order.widthMm ?? 0;
  const ol = order.lengthMm ?? 0;
  if (masterWidthMm <= 0 || masterLengthMm <= 0 || ow <= 0 || ol <= 0) {
    return false;
  }
  return (
    (ow <= masterWidthMm && ol <= masterLengthMm) || (ol <= masterWidthMm && ow <= masterLengthMm)
  );
}
