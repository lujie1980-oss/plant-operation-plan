import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Row, Statistic, Table, Tag } from 'antd';
import { integrationApi, type IntegrationBatch } from '../../api/integrationClient';
import { ADAPTER_CATALOG } from '../../config/integrationAdapters';
import { AntShellPage } from '../../components/shell/AntShellPage';

const batchColumns = [
  { title: '批次 ID', dataIndex: 'importBatchId', key: 'importBatchId', ellipsis: true },
  { title: '适配器', dataIndex: 'adapterId', key: 'adapterId', width: 140 },
  { title: '来源', dataIndex: 'sourceSystem', key: 'sourceSystem', width: 120 },
  { title: '行数', dataIndex: 'rowCount', key: 'rowCount', width: 80 },
  {
    title: '质检',
    dataIndex: 'qualityStatus',
    key: 'qualityStatus',
    width: 100,
    render: (v: string) => {
      const color = v === 'PASSED' ? 'success' : v === 'FAILED' ? 'error' : 'processing';
      return <Tag color={color}>{v || 'PENDING'}</Tag>;
    },
  },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
];

export function IntegrationOverviewPage() {
  const batchesQuery = useQuery({
    queryKey: ['integration', 'batches'],
    queryFn: () => integrationApi.listBatches(),
  });

  const apiPending = batchesQuery.isError;

  return (
    <AntShellPage
      title="数据集成概览"
      description="External staging 导入批次、适配器状态与质检入口（MOD-DI · §19）"
      extra={
        <Button
          icon={<ReloadOutlined />}
          onClick={() => batchesQuery.refetch()}
          loading={batchesQuery.isFetching}
        >
          刷新
        </Button>
      }
    >
      {apiPending ? (
        <Alert
          type="info"
          showIcon
          message="集成 API 待后端实现（TODO-19）"
          description="当前展示 Standard 预设适配器；批次列表将在 GET /api/v1/integration/batches 就绪后自动加载。"
          style={{ marginBottom: 16 }}
        />
      ) : null}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title="预设适配器" value={ADAPTER_CATALOG.length} suffix="个" />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic
              title="最近批次"
              value={batchesQuery.data?.length ?? 0}
              suffix="条"
              loading={batchesQuery.isLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title="数据流" value="external_*" valueStyle={{ fontSize: 18 }} />
          </Card>
        </Col>
      </Row>

      <Card title="预设适配器" size="small" style={{ marginBottom: 16 }}>
        <Table
          size="small"
          pagination={false}
          rowKey="id"
          dataSource={ADAPTER_CATALOG}
          columns={[
            { title: 'ID', dataIndex: 'id', key: 'id', width: 140 },
            { title: '名称', dataIndex: 'name', key: 'name' },
            { title: '类型', dataIndex: 'type', key: 'type', width: 80 },
            { title: '阶段', dataIndex: 'phase', key: 'phase', width: 72 },
            { title: '说明', dataIndex: 'description', key: 'description', ellipsis: true },
          ]}
        />
      </Card>

      <Card title="最近导入批次" size="small">
        <Table<IntegrationBatch>
          size="small"
          rowKey="importBatchId"
          loading={batchesQuery.isLoading}
          dataSource={batchesQuery.data ?? []}
          columns={batchColumns}
          locale={{ emptyText: apiPending ? '等待后端 API' : '暂无导入批次' }}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
        />
      </Card>
    </AntShellPage>
  );
}
