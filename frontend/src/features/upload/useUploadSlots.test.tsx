import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

import type { Asset, Template } from "@/lib/api/types";
import { api } from "@/lib/api/endpoints";
import { useUploadSlots } from "./useUploadSlots";

vi.mock("@/lib/api/endpoints", () => ({
  api: {
    uploadAsset: vi.fn(),
  },
}));

const uploadAsset = vi.mocked(api.uploadAsset);

const template: Template = {
  id: "00000000-0000-4000-8000-000000000201",
  categoryId: "00000000-0000-4000-8000-000000000002",
  title: "海岛旅行明信片",
  description: "需要两张图",
  coverUrl: "/api/v1/templates/x/cover",
  requiredImageCount: 2,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5 * 1024 * 1024,
};

function file(name: string, type = "image/png", size = 1024): File {
  const blob = new Blob([new Uint8Array(size)], { type });
  return new File([blob], name, { type });
}

function asset(id: string): Asset {
  return {
    id,
    templateId: template.id,
    originalName: `${id}.png`,
    mimeType: "image/png",
    sizeBytes: 1024,
    width: 1200,
    height: 900,
    previewUrl: `/api/v1/assets/${id}/content`,
    createdAt: "2026-11-13T02:00:00.000Z",
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

describe("useUploadSlots", () => {
  beforeAll(() => {
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      writable: true,
      value: vi.fn(() => `blob:preview-${Math.random()}`),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      writable: true,
      value: vi.fn(),
    });
  });

  beforeEach(() => {
    uploadAsset.mockReset();
  });

  it("renders exactly the template slot count and gates submission on every slot being READY", async () => {
    uploadAsset.mockResolvedValueOnce(asset("a1")).mockResolvedValueOnce(asset("a2"));
    const { result } = renderHook(() => useUploadSlots(template));

    expect(result.current.slots).toHaveLength(2);
    expect(result.current.canSubmit).toBe(false);

    await act(async () => {
      await result.current.choose(0, file("one.png"));
    });
    expect(result.current.slots[0].status).toBe("READY");
    expect(result.current.canSubmit).toBe(false);

    await act(async () => {
      await result.current.choose(1, file("two.png"));
    });
    expect(result.current.canSubmit).toBe(true);
    expect(result.current.assetIds).toEqual(["a1", "a2"]);
  });

  it("rejects unsupported or oversized files locally, without calling the upload endpoint", async () => {
    const { result } = renderHook(() => useUploadSlots(template));

    await act(async () => {
      await result.current.choose(0, file("bad.svg", "image/svg+xml"));
    });
    expect(result.current.slots[0].status).toBe("ERROR");
    expect(result.current.slots[0].error).toContain("JPEG");

    await act(async () => {
      await result.current.choose(0, file("huge.png", "image/png", 6 * 1024 * 1024));
    });
    expect(result.current.slots[0].status).toBe("ERROR");
    expect(result.current.slots[0].error).toContain("5.0 MB");

    expect(uploadAsset).not.toHaveBeenCalled();
  });

  it("discards a late response after the slot was replaced and aborts the old request", async () => {
    const first = deferred<Asset>();
    uploadAsset.mockReturnValueOnce(first.promise).mockResolvedValueOnce(asset("second"));

    const { result } = renderHook(() => useUploadSlots(template));

    act(() => {
      void result.current.choose(0, file("first.png"));
    });
    await waitFor(() => expect(result.current.slots[0].status).toBe("UPLOADING"));

    await act(async () => {
      await result.current.choose(0, file("second.png"));
    });
    expect(result.current.slots[0].assetId).toBe("second");

    const firstSignal = uploadAsset.mock.calls[0][2];
    expect(firstSignal?.aborted).toBe(true);

    await act(async () => {
      first.resolve(asset("first"));
      await Promise.resolve();
    });
    expect(result.current.slots[0].assetId).toBe("second");
  });

  it("clears a slot on remove and revokes its object URL", async () => {
    uploadAsset.mockResolvedValueOnce(asset("a1"));
    const revoke = vi.mocked(URL.revokeObjectURL);
    const { result } = renderHook(() => useUploadSlots(template));

    act(() => {
      void result.current.choose(0, file("one.png"));
    });
    await waitFor(() => expect(result.current.slots[0].status).toBe("READY"));

    act(() => {
      result.current.remove(0);
    });

    expect(result.current.slots[0].status).toBe("EMPTY");
    expect(result.current.slots[0].assetId).toBeNull();
    expect(result.current.canSubmit).toBe(false);
    expect(revoke).toHaveBeenCalled();
  });
});
