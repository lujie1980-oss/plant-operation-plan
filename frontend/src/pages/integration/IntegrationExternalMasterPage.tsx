import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Table, Tag } from 'antd';
import { integrationApi } from '../../api/integrationClient';
import { AntShellPage } from '../../components/shell/AntShellPage';

const FALLBACK_MASTER_TABLES = [
  { tableName: 'external_stocking_point', label: '库存点' },
  { tableName: 'external_pisp', label: 'PISP 物料' },
  { tableName: 'external_routing', label: '工艺路线' },
  { tableName: 'external_resource', label: '资源' },
  { tableName: 'external_bom', label: 'BOM' },
];

export function IntegrationExternalMasterPage() {
  const tablesQuery = useQuery({
    queryKey: ['integration', 'external', 'master', 'tables'],
    queryFn: () => integrationApi.listExternalTables('master'),
  });

  const rows = tablesQuery.data ?? (tablesQuery.isError ? FALLBACK_MASTER_TABLES : []);
  const apiPending = tablesQuery.isError;

  return (
    <AntShellPage
      title="External 主数据"
      description="§11 external_* staging 表浏览；质检通过后 sync 至 md_*"
    >
      {apiPending ? (
        <Alert
          type="info"
          showIcon
          message="表清单 API 待实现"
          description="下方为 §11 标准表族占位；GET /api/v1/integration/external/master/tables 就绪后可展示行数与跳转明细。"
          style={{ marginBottom: 16 }}
        />
      ) : null}

      <Card size="small">
        <Table
          size="small"
          rowKey="tableName"
          loading={tablesQuery.isLoading}
          dataSource={rows}
          pagination={false}
          columns={[
            { title: '表名', dataIndex: 'tableName', key: 'tableName' },
            { title: '说明', dataIndex: 'label', key: 'label' },
            {
              title: '行数',
              dataIndex: 'rowCount',
              key: 'rowCount',
              width: 100,
              render: (v: number | undefined) => (v != null ? v : '—'),
            },
            {
              title: '域',
              key: 'domain',
              width: 100,
              render: () => <Tag>master</Tag>,
            },
          ]}
        />
      </Card>
    </AntShellPage>
  );
}
