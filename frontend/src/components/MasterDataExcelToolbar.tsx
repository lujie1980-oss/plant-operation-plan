import { useRef, useState } from 'react';
import { api } from '../api/client';
import './MasterDataExcelToolbar.css';

type Props = {
  onImported?: () => void;
};

export function MasterDataExcelToolbar({ onImported }: Props) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState<'template' | 'export' | 'import' | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = async (kind: 'template' | 'export' | 'import', action: () => Promise<void>) => {
    setBusy(kind);
    setError(null);
    setMessage(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : '操作失败');
    } finally {
      setBusy(null);
    }
  };

  const onPickFile = () => {
    fileRef.current?.click();
  };

  const onFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      setError('请上传 .xlsx 格式的 Excel 文件');
      return;
    }
    await run('import', async () => {
      const result = await api.masterData.importExcel(file);
      if (result.errors.length > 0) {
        const preview = result.errors.slice(0, 5).join('；');
        const more = result.errors.length > 5 ? ` 等共 ${result.errors.length} 条` : '';
        setMessage(`已导入 ${result.rowsImported} 行，部分行失败：${preview}${more}`);
      } else {
        setMessage(`导入成功，共 ${result.rowsImported} 行`);
      }
      onImported?.();
    });
  };

  return (
    <div className="md-excel-toolbar-wrap">
      <div className="md-excel-toolbar">
        <button
          type="button"
          className="btn btn-secondary"
          disabled={busy != null}
          onClick={() => void run('template', () => api.masterData.downloadTemplate())}
        >
          {busy === 'template' ? '下载中…' : '下载数据模板'}
        </button>
        <button type="button" className="btn btn-secondary" disabled={busy != null} onClick={onPickFile}>
          {busy === 'import' ? '导入中…' : '导入数据'}
        </button>
        <button
          type="button"
          className="btn"
          disabled={busy != null}
          onClick={() => void run('export', () => api.masterData.exportAll())}
        >
          {busy === 'export' ? '导出中…' : '导出全部数据'}
        </button>
        <input
          ref={fileRef}
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          className="md-excel-file-input"
          onChange={(ev) => void onFileChange(ev)}
        />
      </div>
      {message && <p className="md-excel-msg ok">{message}</p>}
      {error && <p className="md-excel-msg err">{error}</p>}
    </div>
  );
}
