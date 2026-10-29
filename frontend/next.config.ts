import type { NextConfig } from 'next';
const config: NextConfig = {
  output: 'standalone',
  async rewrites() { return [{source:'/api/:path*',destination:`${process.env.JAVA_API_URL || 'http://backend:8080'}/api/:path*`}]; },
};
export default config;
