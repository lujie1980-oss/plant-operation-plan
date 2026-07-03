export type CanvasLabelKey =
  | 'orderCode'
  | 'productCode'
  | 'widthMm'
  | 'lengthMm'
  | 'thicknessMm'
  | 'salesOrderNo';

export const CANVAS_LABEL_OPTIONS: { key: CanvasLabelKey; label: string }[] = [
  { key: 'orderCode', label: '订单号' },
  { key: 'productCode', label: '物料号' },
  { key: 'widthMm', label: '宽度' },
  { key: 'lengthMm', label: '长度' },
  { key: 'thicknessMm', label: '厚度' },
  { key: 'salesOrderNo', label: '销售订单' },
];

const STORAGE_KEY = 'slitting-studio-canvas-labels';

const DEFAULT_KEYS: CanvasLabelKey[] = ['orderCode', 'productCode', 'widthMm', 'thicknessMm'];

export function loadCanvasLabelKeys(): CanvasLabelKey[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return DEFAULT_KEYS;
    }
    const parsed = JSON.parse(raw) as CanvasLabelKey[];
    return parsed.filter((k) => CANVAS_LABEL_OPTIONS.some((o) => o.key === k));
  } catch {
    return DEFAULT_KEYS;
  }
}

export function saveCanvasLabelKeys(keys: CanvasLabelKey[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(keys));
}

export function formatCanvasLabels(
  keys: CanvasLabelKey[],
  data: {
    orderCode?: string;
    productCode?: string;
    widthMm?: number;
    lengthMm?: number;
    thicknessMm?: number;
    salesOrderNo?: string;
    nodeLabel?: string;
  },
): string {
  const lines: string[] = [];
  for (const key of keys) {
    switch (key) {
      case 'orderCode':
        if (data.orderCode) lines.push(`单:${data.orderCode}`);
        break;
      case 'productCode':
        if (data.productCode) lines.push(`料:${data.productCode}`);
        break;
      case 'widthMm':
        if (data.widthMm != null) lines.push(`宽:${data.widthMm}`);
        break;
      case 'lengthMm':
        if (data.lengthMm != null) lines.push(`长:${data.lengthMm}`);
        break;
      case 'thicknessMm':
        if (data.thicknessMm != null) lines.push(`厚:${data.thicknessMm}`);
        break;
      case 'salesOrderNo':
        if (data.salesOrderNo) lines.push(`SO:${data.salesOrderNo}`);
        break;
      default:
        break;
    }
  }
  if (lines.length === 0 && data.nodeLabel) {
    return data.nodeLabel;
  }
  return lines.join('\n');
}
