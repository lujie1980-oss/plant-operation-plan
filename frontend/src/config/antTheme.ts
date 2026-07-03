import type { ThemeConfig } from 'antd';

/** Maps design-tokens.css to Ant Design ConfigProvider theme (L1 Shell). */
export const antTheme: ThemeConfig = {
  token: {
    colorPrimary: '#3b82f6',
    colorLink: '#3b82f6',
    colorText: '#0f172a',
    colorTextSecondary: '#64748b',
    colorBorder: '#e2e8f0',
    colorBgLayout: '#f1f5f9',
    colorBgContainer: '#ffffff',
    borderRadius: 8,
    fontFamily: "'Segoe UI', system-ui, -apple-system, sans-serif",
  },
  components: {
    Layout: {
      bodyBg: '#f1f5f9',
      headerBg: '#ffffff',
    },
    Menu: {
      itemBorderRadius: 6,
    },
    Table: {
      headerBg: '#f8fafc',
    },
  },
};
