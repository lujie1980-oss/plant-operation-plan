import { useCallback, useEffect, useRef, useState } from 'react';
import { slittingClient } from '../../api/slittingClient';
import { EditableTable, type EditableColumn } from '../EditableTable';
import type { MasterDataRecord } from '../../types/masterData';
import type { MasterRoll } from '../../types/slitting';
import '../../pages/MasterDataPage.css';

const STATUS_OPTIONS = [
  { value: 'AVAILABLE', label: 'AVAILABLE' },
  { value: 'RESERVED', label: 'RESERVED' },
  { value: 'VIRTUAL', label: 'VIRTUAL' },
  { value: 'ARCHIVED', label: 'ARCHIVED' },
];

type MasterRollRow = MasterRoll &
  MasterDataRecord & {
    _originalRollCode?: string;
  };

const COLUMNS: EditableColumn<MasterRollRow>[] = [
  { key: 'rollCode', label: '编号', type: 'text', required: true, width: 120 },
  { key: 'widthMm', label: '宽度 (mm)', type: 'number', required: true, width: 110 },
  { key: 'lengthMm', label: '长度 (mm)', type: 'number', required: true, width: 120 },
  { key: 'thicknessMm', label: '厚度 (mm)', type: 'number', width: 100 },
  { key: 'productCode', label: '产品料号', type: 'text', width: 140 },
  { key: 'materialCode', label: '材质', type: 'text', width: 120 },
  { key: 'finishedProductCode', label: '成品料号', type: 'text', width: 140 },
  { key: 'kerfLongitudinalMm', label: '纵切刀缝', type: 'number', width: 90 },
  { key: 'kerfTransverseMm', label: '横切刀缝', type: 'number', width: 90 },
  { key: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS, width: 110 },
];

function toRow(roll: MasterRoll, id: number): MasterRollRow {
  return {
    ...roll,
    id,
    kerfLongitudinalMm: roll.kerfLongitudinalMm ?? 2,
    kerfTransverseMm: roll.kerfTransverseMm ?? 2,
    status: roll.status ?? 'AVAILABLE',
    _originalRollCode: roll.rollCode,
  };
}

function toPayload(row: MasterRollRow): MasterRoll {
  return {
    rollCode: row.rollCode.trim(),
    widthMm: row.widthMm,
    lengthMm: row.lengthMm,
    thicknessMm: row.thicknessMm,
    productCode: row.productCode,
    materialCode: row.materialCode,
    finishedProductCode: row.finishedProductCode,
    kerfLongitudinalMm: row.kerfLongitudinalMm ?? 2,
    kerfTransverseMm: row.kerfTransverseMm ?? 2,
    status: row.status ?? 'AVAILABLE',
  };
}

type Props = {
  onDataChange?: () => void;
};

export function SlittingMasterRollTabBody({ onDataChange }: Props) {
  const [rows, setRows] = useState<MasterRollRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const persistedRef = useRef(new Set<string>());

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await slittingClient.listMasterRolls();
      persistedRef.current = new Set(list.map((r) => r.rollCode));
      setRows(list.map((roll, index) => toRow(roll, index + 1)));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSave = async (row: MasterRollRow) => {
    setSaving(true);
    try {
      const payload = toPayload(row);
      const original = row._originalRollCode ?? row.rollCode;
      const isNew = row.id == null || !persistedRef.current.has(original);
      const saved = isNew
        ? await slittingClient.createMasterRoll(payload)
        : await slittingClient.updateMasterRoll(original, payload);
      persistedRef.current.add(saved.rollCode);
      await load();
      onDataChange?.();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: MasterRollRow) => {
    const key = row._originalRollCode ?? row.rollCode;
    if (!key.trim()) return;
    await slittingClient.deleteMasterRoll(key);
    persistedRef.current.delete(key);
    setRows((prev) => prev.filter((r) => (r._originalRollCode ?? r.rollCode) !== key));
    onDataChange?.();
  };

  return (
    <div className="md-tab-body card">
      <p className="md-tab-desc">
        维护可用于分切排样的母卷库存；删除为归档（ARCHIVED），不会在列表中移除时可改状态查看。
      </p>
      {error ? <div className="editable-table-error">{error}</div> : null}
      <EditableTable<MasterRollRow>
        tableId="slitting-master-rolls"
        rows={rows}
        columns={COLUMNS}
        rowKey={(r) => r._originalRollCode ?? r.rollCode}
        emptyRow={() => ({
          id: null,
          rollCode: '',
          widthMm: 730,
          lengthMm: 600_000,
          kerfLongitudinalMm: 2,
          kerfTransverseMm: 2,
          status: 'AVAILABLE',
        })}
        onSave={handleSave}
        onDelete={handleDelete}
        loading={loading}
        saving={saving}
        search={(r) =>
          `${r.rollCode} ${r.productCode ?? ''} ${r.materialCode ?? ''} ${r.finishedProductCode ?? ''}`
        }
      />
    </div>
  );
}
