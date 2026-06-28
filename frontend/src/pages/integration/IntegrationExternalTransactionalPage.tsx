import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Table, Tag } from 'antd';
import { integrationApi } from '../../api/integrationClient';
import { AntShellPage } from '../../components/shell/AntShellPage';

const FALLBACK_TXN_TABLES = [
  { tableName: 'external_customer_order', label: '客户订单' },
  { tableName: 'external_work_order', label: '工单' },
  { tableName: 'external_inventory', label: '库存' },
  { tableName: 'external_purchase_order', label: '采购订单' },
];

export function IntegrationExternalTransactionalPage() {
  const tablesQuery = useQuery({
    queryKey: ['integration', 'external', 'transactional', 'tables'],
    queryFn: () => integrationApi.listExternalTables('transactional'),
  });

  const rows = tablesQuery.data ?? (tablesQuery.isError ? FALLBACK_TXN_TABLES : []);
  const apiPending = tablesQuery.isError;

  return (
    <AntShellPage
      title="External 交易数据"
      description="§12 external_* 交易 staging；Firm WO 等 sync 至 txn_*"
    >
      {apiPending ? (
        <Alert
          type="info"
          showIcon
          message="表清单 API 待实现"
          description="下方为 §12 标准表族占位。"
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
              width: 120,
              render: () => <Tag color="blue">transactional</Tag>,
            },
          ]}
        />
      </Card>
    </AntShellPage>
  );
}
