import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { integrationApi } from '../../api/integrationClient';
import { ADAPTER_CATALOG } from '../../config/integrationAdapters';
import { AntShellPage } from '../../components/shell/AntShellPage';

export function IntegrationAdaptersPage() {
  const adaptersQuery = useQuery({
    queryKey: ['integration', 'adapters'],
    queryFn: () => integrationApi.listAdapters(),
  });

  const apiPending = adaptersQuery.isError;
  const statusById = new Map((adaptersQuery.data ?? []).map((a) => [a.adapterId, a]));

  const rows = ADAPTER_CATALOG.map((cat) => {
    const live = statusById.get(cat.id);
    return {
      ...cat,
      enabled: live?.enabled ?? cat.id === 'ADP-EXCEL',
      configured: live?.configured ?? cat.id === 'ADP-EXCEL',
      lastRunAt: live?.lastRunAt,
      lastStatus: live?.lastStatus,
    };
  });

  return (
    <AntShellPage
      title="数据集成适配器"
      description="ADP-ERP-SAP · ADP-MES · ADP-EXCEL；写入 external_* staging（RULE-MD-01）"
    >
      {apiPending ? (
        <Alert
          type="info"
          showIcon
          message="适配器状态 API 待实现"
          description="默认启用 ADP-EXCEL；SAP/MES 需配置连接后启用。"
          style={{ marginBottom: 16 }}
        />
      ) : null}

      <Card size="small">
        <Table
          size="small"
          rowKey="id"
          loading={adaptersQuery.isLoading}
          dataSource={rows}
          pagination={false}
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 140 },
            { title: '名称', dataIndex: 'name', key: 'name' },
            { title: '类型', dataIndex: 'type', key: 'type', width: 80 },
            {
              title: '启用',
              dataIndex: 'enabled',
              key: 'enabled',
              width: 80,
              render: (v: boolean) => (v ? <Tag color="success">是</Tag> : <Tag>否</Tag>),
            },
            {
              title: '已配置',
              dataIndex: 'configured',
              key: 'configured',
              width: 88,
              render: (v: boolean) => (v ? <Tag color="blue">是</Tag> : <Tag>否</Tag>),
            },
            {
              title: '最近运行',
              dataIndex: 'lastRunAt',
              key: 'lastRunAt',
              width: 160,
              render: (v?: string) => v ?? '—',
            },
            {
              title: '操作',
              key: 'actions',
              width: 120,
              render: (_, row) => (
                <Link to={`/integration/adapters/${row.routeSlug}`}>
                  <Button type="link" size="small">
                    配置
                  </Button>
                </Link>
              ),
            },
          ]}
        />
      </Card>
    </AntShellPage>
  );
}
