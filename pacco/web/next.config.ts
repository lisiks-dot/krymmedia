import type { NextConfig } from "next";

// В проде /api отдаёт Caddy напрямую из бэкенда; rewrite нужен для `npm run dev` и запуска без прокси.
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const config: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default config;
