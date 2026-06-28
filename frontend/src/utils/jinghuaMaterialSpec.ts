import type { MaterialMd } from '../types/masterData';

export interface ParsedMaterialSpec {
  specDescription: string;
  modelCode: string;
  width: number | null;
  length: number | null;
  color: string | null;
  material: string | null;
  pretreatment: string | null;
}

export interface MaterialMasterRow extends ParsedMaterialSpec {
  materialName: string;
  materialType: string | null;
}

const PRETREATMENT_VALUES = new Set(['已处理', '未处理']);

function looksLikeColor(segment: string): boolean {
  return /[黄红蓝绿白黑灰]/.test(segment) && !segment.includes('淋硅');
}

/** 从规格描述（料号）按 "/" 解析结构化字段，规则对齐晶华 MRP 用例。 */
export function parseMaterialSpecDescription(specDescription: string): ParsedMaterialSpec {
  const spec = (specDescription ?? '').trim();
  if (!spec) {
    return {
      specDescription: '—',
      modelCode: '—',
      width: null,
      length: null,
      color: null,
      material: null,
      pretreatment: null,
    };
  }

  const segments = spec.split('/').map((s) => s.trim()).filter(Boolean);
  const modelCode = segments[0] ?? spec;

  let width: number | null = null;
  let length: number | null = null;
  let color: string | null = null;
  let material: string | null = null;
  let pretreatment: string | null = null;
  const materialParts: string[] = [];

  for (let i = 1; i < segments.length; i++) {
    const seg = segments[i];

    if (PRETREATMENT_VALUES.has(seg)) {
      pretreatment = seg;
      continue;
    }

    const starLength = seg.match(/^(\d+(?:\.\d+)?)\*(\d+(?:\.\d+)?)\s*M$/i);
    if (starLength) {
      width = Number(starLength[1]);
      length = Number(starLength[2]);
      continue;
    }

    const mm = seg.match(/^(\d+(?:\.\d+)?)\s*mm$/i);
    if (mm) {
      width = Number(mm[1]);
      continue;
    }

    const lengthOnly = seg.match(/^(\d+(?:\.\d+)?)\s*M$/i);
    if (lengthOnly) {
      material = seg;
      continue;
    }

    if (/^\d+R$/i.test(seg)) {
      continue;
    }

    if (looksLikeColor(seg)) {
      color = seg;
      continue;
    }

    materialParts.push(seg);
  }

  if (materialParts.length > 0) {
    const joined = materialParts.join(' / ');
    material = material ? `${material} / ${joined}` : joined;
  }

  return {
    specDescription: spec,
    modelCode,
    width,
    length,
    color,
    material,
    pretreatment,
  };
}

export function buildMaterialMasterRows(
  materials: MaterialMd[],
  allowedCodes?: ReadonlySet<string>,
): MaterialMasterRow[] {
  const seen = new Set<string>();
  const rows: MaterialMasterRow[] = [];

  for (const m of materials) {
    const code = m.materialCode?.trim();
    if (!code || seen.has(code)) {
      continue;
    }
    if (allowedCodes && !allowedCodes.has(code)) {
      continue;
    }
    seen.add(code);
    rows.push({
      materialName: m.materialName?.trim() || '—',
      materialType: m.materialType ?? null,
      ...parseMaterialSpecDescription(code),
    });
  }

  return rows.sort((a, b) =>
    a.specDescription.localeCompare(b.specDescription, 'zh-CN'),
  );
}

export function fmtSpecNumber(value: number | null): string {
  if (value == null || Number.isNaN(value)) {
    return '—';
  }
  if (Number.isInteger(value)) {
    return String(value);
  }
  return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
}
