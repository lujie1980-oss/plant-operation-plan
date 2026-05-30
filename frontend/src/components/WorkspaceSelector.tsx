import { useWorkspace } from '../context/WorkspaceContext';
import './WorkspaceSelector.css';

export function WorkspaceSelector() {
  const { workspaceId, workspaces, loading, setWorkspaceId } = useWorkspace();
  const current = workspaces.find((w) => w.workspaceId === workspaceId);

  return (
    <div className="workspace-selector" role="group" aria-label="当前数据集">
      <label className="workspace-selector-label" htmlFor="workspace-select">
        当前数据集
      </label>
      <select
        id="workspace-select"
        value={workspaceId}
        disabled={loading || workspaces.length === 0}
        onChange={(e) => setWorkspaceId(e.target.value)}
        title={current?.description ?? current?.name}
      >
        {workspaces.map((w) => (
          <option key={w.workspaceId} value={w.workspaceId}>
            {w.name}
            {w.isDefault ? '（默认）' : ''}
          </option>
        ))}
      </select>
      {current?.description && (
        <span className="workspace-selector-hint" title={current.description}>
          {current.description}
        </span>
      )}
    </div>
  );
}
