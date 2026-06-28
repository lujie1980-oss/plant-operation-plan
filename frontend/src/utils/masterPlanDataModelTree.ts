import type { MasterPlanDataModelTree } from '../types/masterPlanDataModel';

export type { StockingPointNode } from '../types/masterPlanDataModel';

export interface SpPispTreeRow {
  rowKey: string;
  depth: number;
  nodeType: 'SP' | 'PISP';
  stockingPointId: string;
  stockingPointCode: string;
  displayName: string;
  pispId: string | null;
  productCode: string | null;
  productName: string | null;
  bomTierLabel: string | null;
  hasRouting: boolean | null;
  pispCount: number | null;
}

export function flattenSpPispTree(tree: MasterPlanDataModelTree | null): SpPispTreeRow[] {
  if (!tree) return [];
  const rows: SpPispTreeRow[] = [];
  for (const sp of tree.stockingPoints) {
    rows.push({
      rowKey: `sp:${sp.id}`,
      depth: 0,
      nodeType: 'SP',
      stockingPointId: sp.id,
      stockingPointCode: sp.stockingPointCode,
      displayName: sp.displayName,
      pispId: null,
      productCode: null,
      productName: null,
      bomTierLabel: null,
      hasRouting: null,
      pispCount: sp.pisps.length,
    });
    for (const pisp of sp.pisps) {
      rows.push({
        rowKey: `pisp:${pisp.pispId}`,
        depth: 1,
        nodeType: 'PISP',
        stockingPointId: sp.id,
        stockingPointCode: sp.stockingPointCode,
        displayName: sp.displayName,
        pispId: pisp.pispId,
        productCode: pisp.productCode,
        productName: pisp.productName,
        bomTierLabel: pisp.bomTierLabel,
        hasRouting: pisp.hasRouting,
        pispCount: null,
      });
    }
  }
  return rows;
}

export function visibleSpPispRows(
  rows: SpPispTreeRow[],
  collapsedSp: Set<string>,
): SpPispTreeRow[] {
  return rows.filter((row) => row.nodeType === 'SP' || !collapsedSp.has(row.stockingPointId));
}
