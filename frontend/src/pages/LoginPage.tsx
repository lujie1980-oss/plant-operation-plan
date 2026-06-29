import { FormEvent, useState } from 'react';
import { useAuth } from '../providers/AuthContext';
import './LoginPage.css';

export function LoginPage() {
  const { login, registrationEnabled } = useAuth();
  const [loginName, setLoginName] = useState('dev');
  const [password, setPassword] = useState('dev');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(loginName.trim(), password);
      window.location.hash = '#/';
      window.location.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <h1>工厂运营计划</h1>
        <p className="login-sub">登录以继续</p>
        <form className="login-form" onSubmit={(e) => void onSubmit(e)}>
          <label>
            用户名
            <input
              value={loginName}
              onChange={(e) => setLoginName(e.target.value)}
              autoComplete="username"
              required
            />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error && <p className="login-error">{error}</p>}
          <button type="submit" className="btn primary login-submit" disabled={busy}>
            {busy ? '登录中…' : '登录'}
          </button>
        </form>
        {registrationEnabled && (
          <p className="login-note">开放注册已启用，请联系管理员获取注册入口。</p>
        )}
        <p className="login-hint">开发环境默认 dev / dev（关闭 dev-mode 后须使用已创建账号）。</p>
      </div>
    </div>
  );
}
