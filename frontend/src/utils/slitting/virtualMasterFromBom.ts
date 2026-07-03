import type { MasterRoll } from '../../types/slitting';
import { parseMaterialSpecDescription } from '../jinghuaMaterialSpec';

const DEFAULT_WIDTH_MM = 730;
const DEFAULT_LENGTH_MM = 600_000;
const DEFAULT_KERF = 2;

export function bomMaterialRollCode(productCode: string): string {
  return `BOM-${productCode.replace(/[/\\:*?"<>|]/g, '_')}`;
}

export function resolveInventoryMasterForBom(
  productCode: string,
  allMasters: MasterRoll[],
  usedRollCodes: Set<string>,
): MasterRoll | undefined {
  return allMasters.find((m) => {
    if (usedRollCodes.has(m.rollCode)) return false;
    const code = m.productCode ?? m.materialCode ?? '';
    return code === productCode;
  });
}

export function buildVirtualMasterFromBom(
  productCode: string,
  finishedProductCode?: string,
): MasterRoll {
  const spec = parseMaterialSpecDescription(productCode);
  const widthMm = spec.width ?? DEFAULT_WIDTH_MM;
  const lengthMm = spec.length != null ? Math.round(spec.length * 1000) : DEFAULT_LENGTH_MM;
  return {
    rollCode: bomMaterialRollCode(productCode),
    widthMm,
    lengthMm,
    productCode,
    finishedProductCode: finishedProductCode || undefined,
    materialCode: spec.material ?? undefined,
    kerfLongitudinalMm: DEFAULT_KERF,
    kerfTransverseMm: DEFAULT_KERF,
    status: 'VIRTUAL',
  };
}

export function isCatalogMasterRoll(roll: MasterRoll, allMasters: MasterRoll[]): boolean {
  return allMasters.some((m) => m.rollCode === roll.rollCode);
}
