import { Alert, Card, Empty } from 'antd';
import { AntShellPage } from '../../components/shell/AntShellPage';

export function IntegrationQualityPage() {
  return (
    <AntShellPage
      title="质检报告"
      description="按 import_batch_id / quality_issue_codes 查看 external_* 质检结果"
    >
      <Alert
        type="info"
        showIcon
        message="质检报告 API 待实现（TODO-19）"
        description="将对接 MasterDataQualityService / TransactionalDataQualityService；支持按批次筛选与 sync 操作。"
        style={{ marginBottom: 16 }}
      />
      <Card size="small">
        <Empty description="暂无质检报告" />
      </Card>
    </AntShellPage>
  );
}
