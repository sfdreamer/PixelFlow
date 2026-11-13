"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import { errorMessage, setUnauthorizedHandler } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Session } from "@/lib/api/types";

export type IdentityStatus = "loading" | "ready" | "error" | "expired";

interface IdentityState {
  status: IdentityStatus;
  session: Session | null;
  error: string | null;
  /** Changes whenever the identity is (re)established; used to reset page-level state. */
  epoch: number;
}

export interface IdentityValue extends IdentityState {
  reestablish: () => void;
  expireSession: () => void;
}

const IdentityContext = createContext<IdentityValue | null>(null);

export function IdentityProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<IdentityState>({
    status: "loading",
    session: null,
    error: null,
    epoch: 0,
  });

  const expireSession = useCallback(() => {
    setState((current) =>
      // 幂等：并发请求可能同时收到 401，只有一个 401 才应该推进 epoch，
      // 否则依赖 epoch 的加载 effect 会被反复触发，形成请求风暴。
      current.status === "expired"
        ? current
        : { ...current, status: "expired", session: null, epoch: current.epoch + 1 },
    );
  }, []);

  const bootstrap = useCallback(async () => {
    setState((current) => ({ ...current, status: "loading", error: null }));
    try {
      const session = await api.ensureSession();
      setState((current) => ({
        status: "ready",
        session,
        error: null,
        epoch: current.epoch + 1,
      }));
    } catch (error) {
      setState((current) => ({ ...current, status: "error", error: errorMessage(error) }));
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(expireSession);
    void bootstrap();
    return () => setUnauthorizedHandler(null);
  }, [bootstrap, expireSession]);

  const value = useMemo<IdentityValue>(
    () => ({
      ...state,
      reestablish: () => {
        void bootstrap();
      },
      expireSession,
    }),
    [state, bootstrap, expireSession],
  );

  return <IdentityContext.Provider value={value}>{children}</IdentityContext.Provider>;
}

export function useIdentity(): IdentityValue {
  const value = useContext(IdentityContext);
  if (!value) {
    throw new Error("useIdentity must be used inside IdentityProvider");
  }
  return value;
}
