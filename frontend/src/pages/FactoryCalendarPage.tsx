import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import { DECISION_PAGE_HEADER, PageHeader } from '../components/PageHeader';
import { StatusBanner } from '../components/StatusBanner';
import type {
  FactoryCalendarDay,
  FactoryCalendarMonth,
  FactoryCalendarPolicy,
} from '../types/factoryCalendar';
import './FactoryCalendarPage.css';

const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日'];

function defaultPolicy(): FactoryCalendarPolicy {
  return {
    saturdayWork: false,
    sundayWork: false,
    shiftMode: 'TWO',
    shift1Start: '08:00',
    shift1End: '20:00',
    shift2Start: '20:00',
    shift2End: '08:00',
    shift3Start: '00:00',
    shift3End: '08:00',
  };
}

function formatMonthLabel(year: number, month: number) {
  return `${year}年${month}月`;
}

function formatDayTitle(date: string) {
  const d = new Date(`${date}T00:00:00`);
  const dow = WEEKDAY_LABELS[(d.getDay() + 6) % 7];
  return `${date}（周${dow}）`;
}

interface DayEditorState {
  date: string;
  shift1Open: boolean;
  shift2Open: boolean;
  shift3Open: boolean;
  hasOverride: boolean;
  shifts: FactoryCalendarDay['shifts'];
}

export function FactoryCalendarPage() {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth() + 1);
  const [policy, setPolicy] = useState<FactoryCalendarPolicy>(defaultPolicy());
  const [monthData, setMonthData] = useState<FactoryCalendarMonth | null>(null);
  const [loading, setLoading] = useState(false);
  const [savingPolicy, setSavingPolicy] = useState(false);
  const [savingDay, setSavingDay] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [editor, setEditor] = useState<DayEditorState | null>(null);

  const loadMonth = useCallback(async (year: number, month: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.factoryCalendar.getMonth(year, month);
      setMonthData(data);
      setPolicy(data.policy);
    } catch (e) {
      setError(e instanceof Error ? e.message : '日历加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadMonth(viewYear, viewMonth);
  }, [viewYear, viewMonth, loadMonth]);

  const calendarCells = useMemo(() => {
    if (!monthData) return [];
    const first = new Date(viewYear, viewMonth - 1, 1);
    const leading = (first.getDay() + 6) % 7;
    const daysInMonth = new Date(viewYear, viewMonth, 0).getDate();
    const dayMap = new Map(monthData.days.map((d) => [d.date, d]));
    const cells: ({ kind: 'pad' } | { kind: 'day'; day: FactoryCalendarDay })[] = [];
    for (let i = 0; i < leading; i++) {
      cells.push({ kind: 'pad' });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${viewYear}-${String(viewMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const day = dayMap.get(date);
      if (day) {
        cells.push({ kind: 'day', day });
      }
    }
    while (cells.length % 7 !== 0) {
      cells.push({ kind: 'pad' });
    }
    return cells;
  }, [monthData, viewYear, viewMonth]);

  const savePolicy = async () => {
    setSavingPolicy(true);
    setError(null);
    setSuccess(null);
    try {
      const saved = await api.factoryCalendar.savePolicy(policy);
      setPolicy(saved);
      setSuccess('开班模式已保存，并已同步至资源日历（按各产线单班产能 × 开班班次数）');
      await loadMonth(viewYear, viewMonth);
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSavingPolicy(false);
    }
  };

  const openDayEditor = (day: FactoryCalendarDay) => {
    setEditor({
      date: day.date,
      shift1Open: day.shifts.find((s) => s.shiftId === 'S1')?.open ?? false,
      shift2Open: day.shifts.find((s) => s.shiftId === 'S2')?.open ?? false,
      shift3Open: day.shifts.find((s) => s.shiftId === 'S3')?.open ?? false,
      hasOverride: day.hasOverride,
      shifts: day.shifts,
    });
  };

  const saveDayEditor = async (clearOverride: boolean) => {
    if (!editor) return;
    setSavingDay(true);
    setError(null);
    setSuccess(null);
    try {
      await api.factoryCalendar.saveDay({
        date: editor.date,
        shift1Open: editor.shift1Open,
        shift2Open: editor.shift2Open,
        shift3Open: policy.shiftMode === 'THREE' ? editor.shift3Open : null,
        clearOverride,
      });
      setEditor(null);
      setSuccess(clearOverride ? '已恢复该日默认开班规则，并更新资源日历' : '日期开班设定已保存，并更新资源日历');
      await loadMonth(viewYear, viewMonth);
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSavingDay(false);
    }
  };

  const syncCalendars = async () => {
    setSyncing(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.factoryCalendar.sync();
      setSuccess(
        `已同步 ${result.resourceOwnerCount} 个资源/产线，${result.fromDate} 至 ${result.toDate}（${result.horizonDays} 天）的资源可用产能`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : '同步失败');
    } finally {
      setSyncing(false);
    }
  };

  const shiftRows =
    policy.shiftMode === 'THREE'
      ? [
          { key: 'shift1' as const, label: '早班 S1', startKey: 'shift1Start' as const, endKey: 'shift1End' as const },
          { key: 'shift2' as const, label: '晚班 S2', startKey: 'shift2Start' as const, endKey: 'shift2End' as const },
          { key: 'shift3' as const, label: '夜班 S3', startKey: 'shift3Start' as const, endKey: 'shift3End' as const },
        ]
      : [
          { key: 'shift1' as const, label: '早班 S1', startKey: 'shift1Start' as const, endKey: 'shift1End' as const },
          { key: 'shift2' as const, label: '晚班 S2', startKey: 'shift2Start' as const, endKey: 'shift2End' as const },
        ];

  return (
    <div className="factory-calendar-page">
      <PageHeader
        variant={DECISION_PAGE_HEADER}
        title="工厂日历"
        description="配置工厂默认开班模式，并在自然日历上逐日调整各班次是否开工。保存后按产线「单班产能 × 开班班次数」写入资源日历，供主计划计算可用产能。"
      />
      <StatusBanner loading={loading || savingPolicy || savingDay || syncing} error={error} success={success} />

      <section className="card fc-config">
        <div className="fc-config-grid">
          <div>
            <h3 className="fc-section-title">周末开班规则</h3>
            <div className="fc-weekend-row">
              <label>
                <input
                  type="checkbox"
                  checked={policy.saturdayWork}
                  onChange={(e) => setPolicy((p) => ({ ...p, saturdayWork: e.target.checked }))}
                />
                周六默认开班
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={policy.sundayWork}
                  onChange={(e) => setPolicy((p) => ({ ...p, sundayWork: e.target.checked }))}
                />
                周日默认开班
              </label>
            </div>
          </div>
          <div>
            <h3 className="fc-section-title">班次模式</h3>
            <div className="fc-mode-row">
              <label>
                <input
                  type="radio"
                  name="shiftMode"
                  checked={policy.shiftMode === 'TWO'}
                  onChange={() => setPolicy((p) => ({ ...p, shiftMode: 'TWO' }))}
                />
                2 班制
              </label>
              <label>
                <input
                  type="radio"
                  name="shiftMode"
                  checked={policy.shiftMode === 'THREE'}
                  onChange={() => setPolicy((p) => ({ ...p, shiftMode: 'THREE' }))}
                />
                3 班制
              </label>
            </div>
            <div className="fc-shift-times">
              {shiftRows.map((row) => (
                <div key={row.key} className="fc-shift-time-row">
                  <span className="shift-label">{row.label}</span>
                  <input
                    type="time"
                    value={policy[row.startKey]}
                    onChange={(e) =>
                      setPolicy((p) => ({ ...p, [row.startKey]: e.target.value }))
                    }
                  />
                  <span>至</span>
                  <input
                    type="time"
                    value={policy[row.endKey]}
                    onChange={(e) => setPolicy((p) => ({ ...p, [row.endKey]: e.target.value }))}
                  />
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className="fc-config-actions">
          <button type="button" className="btn primary" disabled={savingPolicy} onClick={() => void savePolicy()}>
            保存开班模式
          </button>
          <button type="button" className="btn" disabled={syncing} onClick={() => void syncCalendars()}>
            同步资源日历
          </button>
          <span className="muted-text">
            工作日默认全部班次开工；周末按上方规则。每日可用产能 = 产线单班产能（主数据）× 当日开班班次数；无产线班产能时使用系统默认 480 分钟。
          </span>
        </div>
      </section>

      <section className="card fc-calendar">
        <div className="fc-calendar-head">
          <h3>自然日历</h3>
          <div className="fc-month-nav">
            <button
              type="button"
              className="btn"
              onClick={() => {
                if (viewMonth === 1) {
                  setViewYear((y) => y - 1);
                  setViewMonth(12);
                } else {
                  setViewMonth((m) => m - 1);
                }
              }}
            >
              上月
            </button>
            <span className="month-label">{formatMonthLabel(viewYear, viewMonth)}</span>
            <button
              type="button"
              className="btn"
              onClick={() => {
                if (viewMonth === 12) {
                  setViewYear((y) => y + 1);
                  setViewMonth(1);
                } else {
                  setViewMonth((m) => m + 1);
                }
              }}
            >
              下月
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => {
                const now = new Date();
                setViewYear(now.getFullYear());
                setViewMonth(now.getMonth() + 1);
              }}
            >
              今天
            </button>
          </div>
        </div>
        <div className="fc-calendar-scroll">
          <div className="fc-weekdays">
            {WEEKDAY_LABELS.map((w) => (
              <div key={w} className="fc-weekday">
                周{w}
              </div>
            ))}
          </div>
          <div className="fc-grid">
            {calendarCells.map((cell, idx) => {
              if (cell.kind === 'pad') {
                return <div key={`pad-${idx}`} className="fc-day outside" aria-hidden />;
              }
              const { day } = cell;
              const dayNum = Number(day.date.slice(8, 10));
              return (
                <button
                  key={day.date}
                  type="button"
                  className={`fc-day status-${day.status.toLowerCase()} ${day.hasOverride ? 'has-override' : ''} ${day.weekend ? 'weekend' : ''}`}
                  onClick={() => openDayEditor(day)}
                >
                  <div className="fc-day-num">{dayNum}</div>
                  <div className="fc-day-shifts">
                    {day.shifts.map((s) => (
                      <span
                        key={s.shiftId}
                        className={`fc-shift-dot ${s.open ? 'open' : ''}`}
                        title={`${s.label} ${s.open ? '开' : '停'}`}
                      />
                    ))}
                  </div>
                  <div className="fc-day-meta">
                    {day.openShiftCount === 0
                      ? '停工'
                      : `${day.openShiftCount}/${day.shifts.length} 班 · ${day.totalCapacityMinutes} 分`}
                  </div>
                </button>
              );
            })}
          </div>
          <div className="fc-legend">
            <span className="fc-legend-item">
              <i className="fc-legend-swatch full" /> 全天开班
            </span>
            <span className="fc-legend-item">
              <i className="fc-legend-swatch partial" /> 部分班次
            </span>
            <span className="fc-legend-item">
              <i className="fc-legend-swatch closed" /> 停工
            </span>
            <span className="fc-legend-item">
              <i className="fc-legend-swatch override" /> 已手工调整
            </span>
          </div>
        </div>
      </section>

      {editor && (
        <div className="fc-modal-backdrop" role="presentation" onClick={() => setEditor(null)}>
          <div
            className="fc-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="fc-day-editor-title"
            onClick={(e) => e.stopPropagation()}
          >
            <h4 id="fc-day-editor-title">{formatDayTitle(editor.date)}</h4>
            <p className="fc-modal-sub">
              {editor.hasOverride ? '该日已手工调整；可恢复为默认规则。' : '勾选当日开班的班次。'}
            </p>
            <div className="fc-modal-shifts">
              {editor.shifts.map((shift) => {
                const openKey =
                  shift.shiftId === 'S1'
                    ? 'shift1Open'
                    : shift.shiftId === 'S2'
                      ? 'shift2Open'
                      : 'shift3Open';
                return (
                  <div key={shift.shiftId} className="fc-modal-shift">
                    <label>
                      <input
                        type="checkbox"
                        checked={editor[openKey]}
                        onChange={(e) =>
                          setEditor((prev) =>
                            prev ? { ...prev, [openKey]: e.target.checked } : prev,
                          )
                        }
                      />
                      {shift.label}（{shift.shiftId}）
                    </label>
                    <span className="shift-time">
                      {shift.start} — {shift.end}
                    </span>
                  </div>
                );
              })}
            </div>
            <div className="fc-modal-actions">
              {editor.hasOverride && (
                <button
                  type="button"
                  className="btn"
                  disabled={savingDay}
                  onClick={() => void saveDayEditor(true)}
                >
                  恢复默认
                </button>
              )}
              <button type="button" className="btn" disabled={savingDay} onClick={() => setEditor(null)}>
                取消
              </button>
              <button
                type="button"
                className="btn primary"
                disabled={savingDay}
                onClick={() => void saveDayEditor(false)}
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
