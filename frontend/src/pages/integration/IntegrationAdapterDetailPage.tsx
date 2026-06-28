import { ArrowLeftOutlined, CloudUploadOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Alert, Button, Card, Descriptions, Form, Input, Space, Switch, Upload, message } from 'antd';
import { Link, Navigate, useParams } from 'react-router-dom';
import { integrationApi } from '../../api/integrationClient';
import { ADAPTER_CATALOG, adapterBySlug } from '../../config/integrationAdapters';
import { AntShellPage } from '../../components/shell/AntShellPage';

export function IntegrationAdapterDetailPage() {
  const { adapterSlug } = useParams<{ adapterSlug: string }>();
  const adapter = adapterSlug ? adapterBySlug(adapterSlug) : undefined;

  const runMutation = useMutation({
    mutationFn: () => integrationApi.runAdapter(adapter!.id),
    onSuccess: () => message.success('已触发同步（若后端已实现）'),
    onError: (err: Error) => message.warning(err.message || 'API 尚未就绪'),
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => integrationApi.uploadExcel(file),
    onSuccess: (data) => message.success(`导入批次：${data.importBatchId}`),
    onError: (err: Error) => message.warning(err.message || '上传 API 尚未就绪'),
  });

  if (!adapter) {
    return <Navigate to="/integration/adapters" replace />;
  }

  const isExcel = adapter.id === 'ADP-EXCEL';
  const isSap = adapter.id === 'ADP-ERP-SAP';
  const isMes = adapter.id === 'ADP-MES';

  return (
    <AntShellPage
      title={adapter.name}
      description={adapter.description}
      extra={
        <Link to="/integration/adapters">
          <Button icon={<ArrowLeftOutlined />}>返回列表</Button>
        </Link>
      }
    >
      <Alert
        type="info"
        showIcon
        message="适配器配置 UI 骨架（TODO-19）"
        description="连接参数将存 workspace_adapter_config；密钥走 credentialRef，不落库明文。"
        style={{ marginBottom: 16 }}
      />

      <Card title="基本信息" size="small" style={{ marginBottom: 16 }}>
        <Descriptions size="small" column={2}>
          <Descriptions.Item label="adapterId">{adapter.id}</Descriptions.Item>
          <Descriptions.Item label="类型">{adapter.type}</Descriptions.Item>
          <Descriptions.Item label="阶段">Phase {adapter.phase}</Descriptions.Item>
          <Descriptions.Item label="写入目标">external_*</Descriptions.Item>
        </Descriptions>
      </Card>

      {isSap ? (
        <Card title="SAP 连接" size="small" style={{ marginBottom: 16 }}>
          <Form layout="vertical" disabled>
            <Form.Item label="连接 URL" name="connectionUrl">
              <Input placeholder="https://..." />
            </Form.Item>
            <Form.Item label="Client" name="client">
              <Input placeholder="800" />
            </Form.Item>
            <Form.Item label="用户" name="user">
              <Input />
            </Form.Item>
            <Form.Item label="凭证引用 credentialRef" name="credentialRef">
              <Input placeholder="secret/sap-prod" />
            </Form.Item>
            <Form.Item label="轮询 Cron" name="pollCron">
              <Input placeholder="0 */15 * * * *" />
            </Form.Item>
          </Form>
        </Card>
      ) : null}

      {isMes ? (
        <Card title="MES 连接" size="small" style={{ marginBottom: 16 }}>
          <Form layout="vertical" disabled>
            <Form.Item label="Base URL" name="baseUrl">
              <Input placeholder="https://mes.example/api" />
            </Form.Item>
            <Form.Item label="Plant Code" name="plantCode">
              <Input />
            </Form.Item>
            <Form.Item label="凭证引用" name="credentialRef">
              <Input />
            </Form.Item>
          </Form>
        </Card>
      ) : null}

      {isExcel ? (
        <Card title="Excel 导入" size="small" style={{ marginBottom: 16 }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Form layout="inline">
              <Form.Item label="模板版本">
                <Input disabled defaultValue="2026.1" style={{ width: 120 }} />
              </Form.Item>
              <Form.Item label="仅校验">
                <Switch disabled />
              </Form.Item>
            </Form>
            <Upload
              accept=".xlsx,.xls"
              maxCount={1}
              beforeUpload={(file) => {
                uploadMutation.mutate(file);
                return false;
              }}
            >
              <Button icon={<CloudUploadOutlined />} loading={uploadMutation.isPending}>
                上传 Excel
              </Button>
            </Upload>
          </Space>
        </Card>
      ) : null}

      {!isExcel ? (
        <Card title="运行" size="small">
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={runMutation.isPending}
            onClick={() => runMutation.mutate()}
          >
            立即同步
          </Button>
        </Card>
      ) : null}

      <Card title="Standard 注册表" size="small" style={{ marginTop: 16 }}>
        <pre style={{ margin: 0, fontSize: 12 }}>
          {JSON.stringify(ADAPTER_CATALOG.find((a) => a.id === adapter.id), null, 2)}
        </pre>
      </Card>
    </AntShellPage>
  );
}
