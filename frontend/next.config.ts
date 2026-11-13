import type { NextConfig } from "next";

/**
 * The browser only talks to this Next.js origin. /api/v1/** is forwarded to Java so that the
 * pf_session cookie, Set-Cookie, status codes and multipart bodies pass through untouched.
 * No business logic lives in the proxy.
 */
const javaApiOrigin = process.env.JAVA_API_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${javaApiOrigin}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
