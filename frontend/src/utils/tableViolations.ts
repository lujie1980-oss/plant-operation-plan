import type { MasterDataValidationReportMd, ValidationIssueMd } from '../types/masterData';
import { issueReason } from './masterDataHealthNav';
import type { RowViolation } from '../components/table/types';

export function warningToViolations(message: string | null | undefined): RowViolation[] {
  if (!message?.trim()) return [];
  return [{ level: 'warn', message: message.trim() }];
}

export function mergeViolations(...groups: RowViolation[][]): RowViolation[] {
  return groups.flat();
}

function issueToViolation(issue: ValidationIssueMd): RowViolation {
  return {
    level: issue.severity === 'ERROR' ? 'error' : 'warn',
    ruleCode: issue.ruleId,
    message: issueReason(issue),
  };
}

/** 按 entityKey 索引校验问题，供主数据表行预警列使用 */
export function buildValidationIndexByEntityKey(
  report: MasterDataValidationReportMd | null | undefined,
  entityType: string,
): Map<string, RowViolation[]> {
  const map = new Map<string, RowViolation[]>();
  if (!report) return map;
  const issues = [...report.errors, ...report.warnings].filter((i) => i.entityType === entityType);
  for (const issue of issues) {
    const key = issue.entityKey;
    const list = map.get(key) ?? [];
    list.push(issueToViolation(issue));
    map.set(key, list);
  }
  return map;
}
