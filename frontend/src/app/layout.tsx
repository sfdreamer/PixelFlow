import type { Metadata } from "next";
import type { ReactNode } from "react";

import { IdentityProvider } from "@/features/identity/IdentityProvider";
import { SiteHeader } from "@/components/SiteHeader";
import "./globals.css";

export const metadata: Metadata = {
  title: "PixelFlow · 素材制作",
  description: "选择模板、上传图片并提交模拟素材制作任务，随后查看个人历史与结果。",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <IdentityProvider>
          <SiteHeader />
          <main>{children}</main>
        </IdentityProvider>
      </body>
    </html>
  );
}
