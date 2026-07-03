/** §17.8 UI-NAV-* deep-link query builders and parsers */

export const DEEP_LINK_QUERY = {
  resource: 'resource',
  product: 'product',
  workOrderNo: 'workOrderNo',
} as const;

const FG_STOCKING_POINT = 'FG';

export function pispIdFromProductCode(productCode: string): string {
  return `PISP-${productCode}-${FG_STOCKING_POINT}`;
}

/** Resolve PISP id or bare product code to a product code for table filters. */
export function productCodeFromDeepLinkProduct(product: string): string {
  const trimmed = product.trim();
  if (!trimmed) return trimmed;
  const match = /^PISP-(.+)-[^-]+$/.exec(trimmed);
  return match?.[1] ?? trimmed;
}

export function capacityAnalysisLink(resourceId: string): string {
  const params = new URLSearchParams();
  params.set(DEEP_LINK_QUERY.resource, resourceId);
  return `/master-plan/analysis/capacity?${params.toString()}`;
}

export function materialPlanningLink(pispIdOrProductCode: string): string {
  const params = new URLSearchParams();
  params.set(DEEP_LINK_QUERY.product, pispIdOrProductCode);
  return `/master-plan/analysis/material-planning?${params.toString()}`;
}

export function productionWorkOrdersLink(workOrderNo: string): string {
  const params = new URLSearchParams();
  params.set(DEEP_LINK_QUERY.workOrderNo, workOrderNo);
  return `/master-plan/analysis/work-orders?${params.toString()}`;
}

export function detailScheduleLink(workOrderNo: string): string {
  const params = new URLSearchParams();
  params.set(DEEP_LINK_QUERY.workOrderNo, workOrderNo);
  return `/scheduling/detail-schedule?${params.toString()}`;
}

export function readWorkOrderNoFromSearch(searchParams: URLSearchParams): string | null {
  return searchParams.get(DEEP_LINK_QUERY.workOrderNo) ?? searchParams.get('wo');
}
