import { PageHeader } from '../../components/PageHeader';
import { SlittingStudioWorkbench } from '../../components/slitting/studio/SlittingStudioWorkbench';
import '../../components/slitting/slitting.css';

export function SlittingStudioPage() {
  return (
    <div className="page slitting-module slitting-studio-page">
      <PageHeader title="母卷分切工作台" description="基于母卷拖放排样，区域与子订单图形化编辑" />
      <SlittingStudioWorkbench />
    </div>
  );
}
