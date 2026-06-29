export type AuthConfigDto = {
  devMode: boolean;
  registrationEnabled: boolean;
};

export type AuthTokenDto = {
  accessToken: string;
  tokenType: string;
  expiresInHours: number;
  userId: string;
  displayName: string;
  isSuperAdmin: boolean;
};

export type AdminUser = {
  userId: string;
  loginName: string;
  displayName: string;
  isSuperAdmin: boolean;
  status: string;
  lastLoginAt: string | null;
  createdAt: string;
};

export type AdminWorkspace = {
  workspaceId: string;
  name: string;
  ownerUserId: string | null;
  workspaceType: string | null;
  memberCount: number;
  createdAt: string;
};

export type WorkspaceMember = {
  userId: string;
  displayName: string;
  loginName: string;
  role: string;
};

export type ModulePermission = {
  moduleId: string;
  name: string;
  accessLevel: 'NONE' | 'VIEW' | 'EDIT' | string;
};
