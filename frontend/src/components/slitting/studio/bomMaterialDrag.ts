const DRAG_TYPE = 'slitting/bom-material';

export function bomMaterialDragPayload(productCode: string): string {
  return JSON.stringify({ productCode });
}

export function parseBomMaterialDrag(data: string): { productCode: string } | null {
  try {
    const parsed = JSON.parse(data) as { productCode?: string };
    if (!parsed.productCode?.trim()) return null;
    return { productCode: parsed.productCode.trim() };
  } catch {
    return null;
  }
}

export { DRAG_TYPE as BOM_MATERIAL_DRAG_TYPE };
