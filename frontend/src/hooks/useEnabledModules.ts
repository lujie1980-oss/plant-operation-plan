import { useAuth } from '../providers/AuthContext';

/**
 * Workspace module toggles — from IAM /api/v1/iam/me (TODO-18 M1).
 */
export function useEnabledModules(): Record<string, boolean> {
  const { enabledModules } = useAuth();
  return enabledModules;
}
