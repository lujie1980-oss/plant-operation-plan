import type { MaterialMd } from '../types/masterData';

/** 物料主数据索引：BOM 通过 materialCode 关联 material.id，展示用 materialName。 */
export class MaterialCatalog {
  private readonly byCode = new Map<string, MaterialMd>();

  constructor(materials: MaterialMd[]) {
    for (const m of materials) {
      const code = m.materialCode?.trim();
      if (code) {
        this.byCode.set(code, m);
      }
    }
  }

  get codes(): Set<string> {
    return new Set(this.byCode.keys());
  }

  has(code: string | null | undefined): boolean {
    if (!code?.trim()) {
      return false;
    }
    return this.byCode.has(code.trim());
  }

  get(code: string | null | undefined): MaterialMd | undefined {
    if (!code?.trim()) {
      return undefined;
    }
    return this.byCode.get(code.trim());
  }

  materialId(code: string | null | undefined): number | null {
    return this.get(code)?.id ?? null;
  }

  materialName(code: string | null | undefined): string {
    const name = this.get(code)?.materialName?.trim();
    if (name) {
      return name;
    }
    return code?.trim() || '—';
  }
}
