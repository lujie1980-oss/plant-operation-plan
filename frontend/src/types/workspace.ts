export type Workspace = {
  workspaceId: string;
  name: string;
  description: string | null;
  createdAt: string;
  isDefault: boolean;
};

export type WorkspaceCreatePayload = {
  id: string;
  name: string;
  description?: string;
};
