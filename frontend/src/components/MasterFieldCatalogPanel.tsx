import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type {
  MasterFieldDefinitionCreateMd,
  MasterFieldDefinitionMd,
  MasterFieldDefinitionUpdateMd,
} from '../types/masterData';
import {
  MASTER_FIELD_DATA_TYPE_OPTIONS,
  MASTER_FIELD_ENTITY_OPTIONS,
  MASTER_FIELD_GENERAL_REFERENCE,
} from '../utils/masterFieldGeneralFields';
import '../pages/MasterDataPage.css';

const EMPTY_CREATE: MasterFieldDefinitionCreateMd = {
  entityType: 'MATERIAL',
  fieldKey: '',
  dataType: 'STRING',
  labelZh: '',
  required: false,
  visibleInGrid: true,
  usedInRules: false,
  displayOrder: 100,
};

export function MasterFieldCatalogPanel({ dataRevision = 0 }: { dataRevision?: number }) {
  const [entityType, setEntityType] = useState('MATERIAL');
  const [rows, setRows] = useState<MasterFieldDefinitionMd[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState<MasterFieldDefinitionCreateMd>({ ...EMPTY_CREATE });
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<MasterFieldDefinitionUpdateMd | null>(null);

  const generalRows = useMemo(
    () => MASTER_FIELD_GENERAL_REFERENCE[entityType] ?? [],
    [entityType],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await api.masterData.fieldSchema(entityType);
      setRows(list.filter((r) => r.fieldCategory === 'CUSTOM'));
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载字段目录失败');
    } finally {
      setLoading(false);
    }
  }, [entityType]);

  useEffect(() => {
    void load();
  }, [load, dataRevision]);

  useEffect(() => {
    setCreateForm((prev) => ({ ...prev, entityType }));
    setEditingId(null);
    setEditForm(null);
    setShowCreate(false);
  }, [entityType]);

  const startEdit = (row: MasterFieldDefinitionMd) => {
    if (row.id == null) return;
    setEditingId(row.id);
    setEditForm({
      dataType: row.dataType,
      labelZh: row.labelZh,
      required: row.required,
      visibleInGrid: row.visibleInGrid,
      usedInRules: row.usedInRules,
      displayOrder: row.displayOrder,
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditForm(null);
  };

  const handleCreate = async () => {
    setSaving(true);
    setError(null);
    try {
      await api.masterData.fieldDefinitions.create({ ...createForm, entityType });
      setShowCreate(false);
      setCreateForm({ ...EMPTY_CREATE, entityType });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : '新增失败');
    } finally {
      setSaving(false);
    }
  };

  const handleSaveEdit = async () => {
    if (editingId == null || !editForm) return;
    setSaving(true);
    setError(null);
    try {
      await api.masterData.fieldDefinitions.update(editingId, editForm);
      cancelEdit();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: MasterFieldDefinitionMd) => {
    if (row.id == null) return;
    if (row.source !== 'WORKSPACE') {
      setError('平台预置字段不可删除');
      return;
    }
    if (!window.confirm(`确认删除扩展字段「${row.labelZh}」(${row.fieldKey})？`)) return;
    setSaving(true);
    setError(null);
    try {
      await api.masterData.fieldDefinitions.delete(row.id);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : '删除失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="md-tab-body card mf-catalog">
      <p className="md-tab-desc">
        按 workspace 配置主数据扩展字段。General 为系统固定列；Custom 存入 JSON 扩展区，并驱动主数据表格动态列。
      </p>

      <div className="mf-catalog-toolbar">
        <label className="mf-catalog-label">
          实体类型
          <select
            className="input mf-catalog-select"
            value={entityType}
            onChange={(e) => setEntityType(e.target.value)}
          >
            {MASTER_FIELD_ENTITY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className="btn primary"
          disabled={saving || loading}
          onClick={() => setShowCreate((v) => !v)}
        >
          {showCreate ? '取消新增' : '新增 Custom 字段'}
        </button>
      </div>

      {error && <div className="editable-table-error">{error}</div>}

      {showCreate && (
        <div className="mf-catalog-form card">
          <h3 className="mf-catalog-form-title">新增扩展字段</h3>
          <div className="mf-catalog-form-grid">
            <label>
              字段键 (camelCase)
              <input
                className="input"
                value={createForm.fieldKey}
                placeholder="如 harnessFamily"
                onChange={(e) => setCreateForm((f) => ({ ...f, fieldKey: e.target.value }))}
              />
            </label>
            <label>
              显示名称
              <input
                className="input"
                value={createForm.labelZh}
                onChange={(e) => setCreateForm((f) => ({ ...f, labelZh: e.target.value }))}
              />
            </label>
            <label>
              数据类型
              <select
                className="input"
                value={createForm.dataType}
                onChange={(e) =>
                  setCreateForm((f) => ({
                    ...f,
                    dataType: e.target.value as MasterFieldDefinitionCreateMd['dataType'],
                  }))
                }
              >
                {MASTER_FIELD_DATA_TYPE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              排序
              <input
                className="input"
                type="number"
                value={createForm.displayOrder}
                onChange={(e) =>
                  setCreateForm((f) => ({ ...f, displayOrder: Number.parseInt(e.target.value, 10) || 0 }))
                }
              />
            </label>
          </div>
          <div className="mf-catalog-form-flags">
            <label>
              <input
                type="checkbox"
                checked={createForm.required}
                onChange={(e) => setCreateForm((f) => ({ ...f, required: e.target.checked }))}
              />
              必填
            </label>
            <label>
              <input
                type="checkbox"
                checked={createForm.visibleInGrid}
                onChange={(e) => setCreateForm((f) => ({ ...f, visibleInGrid: e.target.checked }))}
              />
              表格显示
            </label>
            <label>
              <input
                type="checkbox"
                checked={createForm.usedInRules}
                onChange={(e) => setCreateForm((f) => ({ ...f, usedInRules: e.target.checked }))}
              />
              可用于规则/换型
            </label>
          </div>
          <button type="button" className="btn primary" disabled={saving} onClick={() => void handleCreate()}>
            保存
          </button>
        </div>
      )}

      <section className="mf-catalog-section">
        <h3 className="mf-catalog-section-title">General（固定字段）</h3>
        <div className="editable-table-scroll">
          <table className="data-table md-table">
            <thead>
              <tr>
                <th>字段键</th>
                <th>显示名称</th>
                <th>类型</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              {generalRows.map((g) => (
                <tr key={g.fieldKey}>
                  <td>
                    <code>{g.fieldKey}</code>
                  </td>
                  <td>{g.labelZh}</td>
                  <td>{g.dataType}</td>
                  <td className="md-muted">系统内置，不可在此修改</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mf-catalog-section">
        <h3 className="mf-catalog-section-title">Custom（扩展字段）</h3>
        {loading ? (
          <p className="md-muted">加载中…</p>
        ) : (
          <div className="editable-table-scroll">
            <table className="data-table md-table">
              <thead>
                <tr>
                  <th>字段键</th>
                  <th>显示名称</th>
                  <th>类型</th>
                  <th>必填</th>
                  <th>表格</th>
                  <th>规则</th>
                  <th>排序</th>
                  <th>来源</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="md-muted">
                      暂无 Custom 字段，可点击「新增 Custom 字段」
                    </td>
                  </tr>
                ) : (
                  rows.map((row) => {
                    const editing = editingId === row.id && editForm;
                    return (
                      <tr key={row.id ?? row.fieldKey}>
                        <td>
                          <code>{row.fieldKey}</code>
                        </td>
                        <td>
                          {editing ? (
                            <input
                              className="md-input"
                              value={editForm.labelZh}
                              onChange={(e) => setEditForm((f) => f && { ...f, labelZh: e.target.value })}
                            />
                          ) : (
                            row.labelZh
                          )}
                        </td>
                        <td>
                          {editing ? (
                            <select
                              className="md-input"
                              value={editForm.dataType}
                              onChange={(e) =>
                                setEditForm(
                                  (f) =>
                                    f && {
                                      ...f,
                                      dataType: e.target.value as MasterFieldDefinitionUpdateMd['dataType'],
                                    },
                                )
                              }
                            >
                              {MASTER_FIELD_DATA_TYPE_OPTIONS.map((opt) => (
                                <option key={opt.value} value={opt.value}>
                                  {opt.label}
                                </option>
                              ))}
                            </select>
                          ) : (
                            row.dataType
                          )}
                        </td>
                        <td>
                          {editing ? (
                            <input
                              type="checkbox"
                              checked={editForm.required}
                              onChange={(e) =>
                                setEditForm((f) => f && { ...f, required: e.target.checked })
                              }
                            />
                          ) : row.required ? (
                            '是'
                          ) : (
                            '—'
                          )}
                        </td>
                        <td>
                          {editing ? (
                            <input
                              type="checkbox"
                              checked={editForm.visibleInGrid}
                              onChange={(e) =>
                                setEditForm((f) => f && { ...f, visibleInGrid: e.target.checked })
                              }
                            />
                          ) : row.visibleInGrid ? (
                            '是'
                          ) : (
                            '—'
                          )}
                        </td>
                        <td>
                          {editing ? (
                            <input
                              type="checkbox"
                              checked={editForm.usedInRules}
                              onChange={(e) =>
                                setEditForm((f) => f && { ...f, usedInRules: e.target.checked })
                              }
                            />
                          ) : row.usedInRules ? (
                            '是'
                          ) : (
                            '—'
                          )}
                        </td>
                        <td>
                          {editing ? (
                            <input
                              className="md-input"
                              type="number"
                              value={editForm.displayOrder}
                              onChange={(e) =>
                                setEditForm((f) => f && {
                                  ...f,
                                  displayOrder: Number.parseInt(e.target.value, 10) || 0,
                                })
                              }
                            />
                          ) : (
                            row.displayOrder
                          )}
                        </td>
                        <td>{row.source === 'PLATFORM' ? '平台' : '本 workspace'}</td>
                        <td className="md-actions-col">
                          {editing ? (
                            <>
                              <button
                                type="button"
                                className="btn btn-secondary md-btn"
                                disabled={saving}
                                onClick={() => void handleSaveEdit()}
                              >
                                保存
                              </button>
                              <button type="button" className="btn md-btn" onClick={cancelEdit}>
                                取消
                              </button>
                            </>
                          ) : (
                            <>
                              <button
                                type="button"
                                className="btn btn-secondary md-btn"
                                disabled={saving}
                                onClick={() => startEdit(row)}
                              >
                                编辑
                              </button>
                              {row.source === 'WORKSPACE' && (
                                <button
                                  type="button"
                                  className="btn md-btn mf-danger-btn"
                                  disabled={saving}
                                  onClick={() => void handleDelete(row)}
                                >
                                  删除
                                </button>
                              )}
                            </>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
