/** 跨页主数据表格定位（sessionStorage，消费后清除） */
export const MASTER_DATA_FOCUS_KEY = 'plantops.masterDataFocus';

export type MasterDataFocusPage =
  | 'master-data'
  | 'business-data'
  | 'master-plan-rules'
  | 'scheduling-rules';

/** @deprecated 旧全局业务规则页；消费时仍兼容 */
export type LegacyBusinessRulesFocusPage = 'business-rules';

export interface MasterDataTableFocus {
  page: MasterDataFocusPage | LegacyBusinessRulesFocusPage;
  tabId: string;
  /** 填入表格搜索框，缩小可见行 */
  searchQuery: string;
  /** 与 TabConfig.rowKey 一致，用于高亮行 */
  highlightRowKey?: string;
}

export function setMasterDataTableFocus(focus: MasterDataTableFocus): void {
  sessionStorage.setItem(MASTER_DATA_FOCUS_KEY, JSON.stringify(focus));
}

export function peekMasterDataTableFocus(): MasterDataTableFocus | null {
  try {
    const raw = sessionStorage.getItem(MASTER_DATA_FOCUS_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as MasterDataTableFocus;
  } catch {
    return null;
  }
}

export function consumeMasterDataTableFocus(
  expectedPage?: MasterDataFocusPage | LegacyBusinessRulesFocusPage,
): MasterDataTableFocus | null {
  const focus = peekMasterDataTableFocus();
  if (!focus) return null;
  if (expectedPage && focus.page !== expectedPage) return null;
  sessionStorage.removeItem(MASTER_DATA_FOCUS_KEY);
  return focus;
}
