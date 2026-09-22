import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // Capacitor 吃靜態檔：禁 server / API Route / SSR（見 rules §4）
  output: 'export',
  images: { unoptimized: true },
};

export default nextConfig;
