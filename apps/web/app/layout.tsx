import './globals.css';
import type { Viewport } from 'next';

// 沒有 viewport meta 的頁面會被當 980px 桌面版渲染：舊 WebView 啟用
// double-tap-zoom 手勢，使單 tap 只聚焦不觸發 click（API 29 實測）。
// maximumScale=1 關掉雙擊縮放，點擊一次到位。
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-Hant">
      <body>{children}</body>
    </html>
  );
}
