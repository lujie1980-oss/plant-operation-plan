export type Workspace = {
  workspaceId: string;
  name: string;
  description: string | null;
  createdAt: string;
  isDefault: boolean;
  ownerUserId?: string;
  workspaceType?: string;
};

export type WorkspaceCreatePayload = {
  id: string;
  name: string;
  description?: string;
};

export type CurrentUser = {
  userId: string;
  displayName: string;
  isSuperAdmin: boolean;
  hasWorkspaces: boolean;
  workspaces: WorkspaceMembership[];
};

export type WorkspaceMembership = {
  workspaceId: string;
  name: string;
  role: string;
  enabledModules: string[];
};
