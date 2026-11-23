import { apiRequest } from "@/lib/api";

export type VaultItem = {
  id: string;
  name: string;
  kind: "file" | "folder";
  sizeBytes: number;
  mime: string;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
  parentId?: string | null;
  thumbnailUrl?: string | null;
  downloadUrl?: string | null;
};
export type Breadcrumb = { id: string; name: string };
export type FileListing = { items: VaultItem[]; breadcrumbs: Breadcrumb[] };

export function optimisticallyUpdateItems(items: VaultItem[], action: { type: string; ids: string[]; name?: string }): VaultItem[] {
  if (action.type === "rename") return items.map((item) => item.id === action.ids[0] ? { ...item, name: action.name ?? item.name } : item);
  if (action.type === "trash" || action.type === "purge" || action.type === "move") return items.filter((item) => !action.ids.includes(item.id));
  return items;
}

const enc = encodeURIComponent;
export const filesApi = {
  list: (folderId: string, signal?: AbortSignal) =>
    apiRequest<FileListing>(`/api/v1/files?folderId=${enc(folderId)}`, { signal }),
  recent: (signal?: AbortSignal) =>
    apiRequest<FileListing>(`/api/v1/files/recent`, { signal }),
  trash: (signal?: AbortSignal) =>
    apiRequest<FileListing>(`/api/v1/files/trash`, { signal }),
  search: (q: string, folderId: string, signal?: AbortSignal) =>
    apiRequest<VaultItem[]>(`/api/v1/files/search?q=${enc(q)}&folderId=${enc(folderId)}`, { signal }),
  createFolder: (name: string, parentId: string) =>
    apiRequest<VaultItem>(`/api/v1/folders`, { method: "POST", body: JSON.stringify({ name, parentId }) }),
  rename: (id: string, name: string) =>
    apiRequest<VaultItem>(`/api/v1/files/${enc(id)}`, { method: "PATCH", body: JSON.stringify({ name }) }),
  move: (ids: string[], folderId: string | null) =>
    apiRequest<void>(`/api/v1/files/move`, { method: "POST", body: JSON.stringify({ ids, folderId }) }),
  trashItems: (ids: string[]) =>
    apiRequest<void>(`/api/v1/files/trash`, { method: "POST", body: JSON.stringify({ ids }) }),
  restore: (ids: string[]) =>
    apiRequest<void>(`/api/v1/files/restore`, { method: "POST", body: JSON.stringify({ ids }) }),
  purge: (ids: string[]) =>
    apiRequest<void>(`/api/v1/files/permanent-delete`, { method: "POST", body: JSON.stringify({ ids }) }),
  uploadInit: (file: File, folderId: string) =>
    apiRequest<{ uploadId: string; partSize: number; parts: number[] }>(`/api/v1/uploads`, { method: "POST", body: JSON.stringify({ fileName: file.name, sizeBytes: file.size, mime: file.type || "application/octet-stream", folderId }) }),
  uploadStatus: (id: string) => apiRequest<{ partSize: number; completedParts: number[]; status: string }>(`/api/v1/uploads/${enc(id)}`),
  uploadPart: async (id: string, partNumber: number, blob: Blob, signal: AbortSignal, progress: (n: number) => void) => {
    const form = new FormData(); form.append("part", blob);
    // XHR is used for upload progress; auth is supplied by the same in-memory token store.
    const { tokenStore } = await import("@/lib/api");
    return new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest(); xhr.open("PUT", `${(import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "")}/api/v1/uploads/${enc(id)}/parts/${partNumber}`);
      xhr.withCredentials = true; const token = tokenStore.get(); if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
      xhr.upload.onprogress = (event) => { if (event.lengthComputable) progress(event.loaded / event.total); };
      xhr.onload = () => xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Part upload failed (${xhr.status})`));
      xhr.onerror = () => reject(new Error("Network error while uploading part"));
      xhr.onabort = () => reject(new DOMException("Upload paused", "AbortError"));
      signal.addEventListener("abort", () => xhr.abort(), { once: true }); xhr.send(form);
    });
  },
  completeUpload: (id: string) => apiRequest<VaultItem>(`/api/v1/uploads/${enc(id)}/complete`, { method: "POST" }),
  cancelUpload: (id: string) => apiRequest<void>(`/api/v1/uploads/${enc(id)}`, { method: "DELETE" }),
};

export type ShareLink = { id: string; expires_at?: string | null; max_downloads?: number | null; download_count: number; revoked: boolean; password_protected: boolean };
export type CreatedShare = { id: string; token: string; expiresAt?: string | null; maxDownloads?: number | null; fileName: string; sizeBytes: number; mime: string };
export const sharesApi = {
  create: (fileId: string, options: { expiresAt?: string; maxDownloads?: number; password?: string }) => apiRequest<CreatedShare>("/api/v1/shares", { method: "POST", body: JSON.stringify({ fileId, ...options }) }),
  list: (fileId: string) => apiRequest<ShareLink[]>(`/api/v1/shares/file/${encodeURIComponent(fileId)}`),
  revoke: (id: string) => apiRequest<void>(`/api/v1/shares/${encodeURIComponent(id)}`, { method: "DELETE" }),
};

export function toggleSelection(selected: string[], id: string, options: { shift?: boolean; additive?: boolean; index: number; items: VaultItem[]; anchor?: number }): { selected: string[]; anchor: number } {
  if (options.shift && options.anchor !== undefined) {
    const start = Math.min(options.anchor, options.index), end = Math.max(options.anchor, options.index);
    const range = options.items.slice(start, end + 1).map((item) => item.id);
    return { selected: options.additive ? [...new Set([...selected, ...range])] : range, anchor: options.anchor };
  }
  if (options.additive) return { selected: selected.includes(id) ? selected.filter((item) => item !== id) : [...selected, id], anchor: options.index };
  return { selected: selected.length === 1 && selected[0] === id ? [] : [id], anchor: options.index };
}

export function formatBytes(bytes: number) {
  if (!bytes) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"]; const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`;
}
