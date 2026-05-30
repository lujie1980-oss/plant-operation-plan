import { useRef, useState } from 'react';

import { api, type MasterDataImportResult } from '../api/client';

import './MasterDataExcelToolbar.css';



export type BusinessRuleExcelTabId =
  | 'changeover'
  | 'parallel-operations'
  | 'operation-transfer-time'
  | 'continuous-production'
  | 'bom-rules'
  | 'shift-headcount-rules'
  | 'demand-priority-rules';

const EXCEL_TABS = new Set<string>([
  'changeover',
  'parallel-operations',
  'operation-transfer-time',
  'continuous-production',
  'bom-rules',
  'shift-headcount-rules',
  'demand-priority-rules',
]);

const HINTS: Record<BusinessRuleExcelTabId, string> = {
  changeover: '工作表 KTPrefixDuration：工序、属性、前/后任务属性值、换型时长（HH:MM:SS）',
  'parallel-operations': 'U型线清单：半品第一头PN、半品第二头PN、机台（产线 ID）',
  'operation-transfer-time': '工作表「工序流转时间」：产品、前工序、后工序、流转时间、最小流转时间（HH:MM:SS）',
  'continuous-production': '连续生产料号清单：半品第一头PN、半品第二头PN、成品、机台',
  'bom-rules': '工作表「BOM」：成品/父产品/组件、关键件标记等',
  'shift-headcount-rules': '工作表「班次人员」：区域、日期、班次、可用人数',
  'demand-priority-rules': '工作表「订单优先级」：订单号、行号、优先级、加急等级、排程锁定、交期',
};



type Props = {

  activeTabId: string;

  onImported?: () => void;

};



export function BusinessRulesExcelToolbar({ activeTabId, onImported }: Props) {

  const fileRef = useRef<HTMLInputElement>(null);

  const [busy, setBusy] = useState<'template' | 'export' | 'import' | null>(null);

  const [message, setMessage] = useState<string | null>(null);

  const [error, setError] = useState<string | null>(null);



  if (!EXCEL_TABS.has(activeTabId)) {

    return null;

  }



  const kind = activeTabId as BusinessRuleExcelTabId;

  const hint = HINTS[kind];



  const run = async (action: 'template' | 'export' | 'import', fn: () => Promise<void>) => {

    setBusy(action);

    setError(null);

    setMessage(null);

    try {

      await fn();

    } catch (err) {

      setError(err instanceof Error ? err.message : '操作失败');

    } finally {

      setBusy(null);

    }

  };



  const onPickFile = () => {

    fileRef.current?.click();

  };



  const handleImportResult = (result: MasterDataImportResult) => {

    if (result.errors.length > 0) {

      const preview = result.errors.slice(0, 5).join('；');

      const more = result.errors.length > 5 ? ` 等共 ${result.errors.length} 条` : '';

      setMessage(`已导入 ${result.rowsImported} 行，部分行失败：${preview}${more}`);

    } else {

      setMessage(`导入成功，共 ${result.rowsImported} 行`);

    }

    onImported?.();

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

      const result = await api.businessRuleExcel.importRules(kind, file, true);

      handleImportResult(result);

    });

  };



  return (

    <div className="md-excel-toolbar-wrap br-excel-toolbar">

      <p className="br-excel-hint">{hint}</p>

      <div className="md-excel-toolbar">

        <button

          type="button"

          className="btn btn-secondary"

          disabled={busy != null}

          onClick={() => void run('template', () => api.businessRuleExcel.downloadTemplate(kind))}

        >

          {busy === 'template' ? '下载中…' : '下载规则模板'}

        </button>

        <button type="button" className="btn btn-secondary" disabled={busy != null} onClick={onPickFile}>

          {busy === 'import' ? '导入中…' : '导入规则'}

        </button>

        <button

          type="button"

          className="btn"

          disabled={busy != null}

          onClick={() => void run('export', () => api.businessRuleExcel.exportRules(kind))}

        >

          {busy === 'export' ? '导出中…' : '导出规则'}

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

