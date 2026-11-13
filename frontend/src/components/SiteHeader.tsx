"use client";

import Link from "next/link";

import { useIdentity } from "@/features/identity/IdentityProvider";
import styles from "./SiteHeader.module.css";

export function SiteHeader() {
  const { status, session, reestablish } = useIdentity();

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link href="/" className={styles.brand} aria-label="PixelFlow 首页">
          <span className={styles.mark} aria-hidden="true" />
          <span className={styles.name}>PixelFlow</span>
          <span className={styles.tagline}>素材制作</span>
        </Link>

        <nav className={styles.nav} aria-label="主导航">
          <Link href="/" className={styles.navLink}>
            模板库
          </Link>
          <Link href="/history" className={styles.navLink}>
            我的历史
          </Link>
        </nav>

        <div className={styles.identity}>
          {status === "ready" && session ? (
            <span className={styles.chip}>
              <span className={styles.chipName}>{session.displayName}</span>
              <span className={styles.chipHint}>匿名身份</span>
            </span>
          ) : null}
          {status === "loading" ? <span className={styles.chip}>正在建立会话…</span> : null}
          {status === "expired" ? (
            <>
              <span className={styles.alert}>会话已失效</span>
              <button type="button" className={styles.action} onClick={reestablish}>
                重新建立会话
              </button>
            </>
          ) : null}
          {status === "error" ? (
            <>
              <span className={styles.alert}>会话建立失败</span>
              <button type="button" className={styles.action} onClick={reestablish}>
                重试
              </button>
            </>
          ) : null}
        </div>
      </div>
    </header>
  );
}
