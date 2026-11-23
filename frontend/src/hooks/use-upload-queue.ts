import { useCallback, useEffect, useRef, useState } from "react";
import { filesApi } from "@/lib/files";

export type UploadStatus = "queued" | "uploading" | "paused" | "complete" | "error" | "cancelled";
export type UploadTask = { id: string; file: File; folderId: string; status: UploadStatus; uploadId?: string; progress: number; speed: number; eta: number; error?: string };
export function transitionUploadTask(tasks: UploadTask[], id: string, patch: Partial<UploadTask>): UploadTask[] {
  return tasks.map((task) => task.id === id ? { ...task, ...patch } : task);
}

export function useUploadQueue() {
  const [tasks, setTasks] = useState<UploadTask[]>([]);
  const tasksRef = useRef(tasks); tasksRef.current = tasks;
  const active = useRef(new Map<string, AbortController>());
  const busyFiles = useRef(0);
  const mutate = (id: string, patch: Partial<UploadTask>) => setTasks((items) => transitionUploadTask(items, id, patch));
  const pumpRef = useRef<() => void>(() => {});
  const run = useCallback(async (task: UploadTask) => {
    busyFiles.current++; const started = Date.now();
    try {
      let uploadId = task.uploadId;
      if (!uploadId) { const init = await filesApi.uploadInit(task.file, task.folderId); uploadId = init.uploadId; mutate(task.id, { uploadId }); }
      const status = await filesApi.uploadStatus(uploadId);
      const partSize = status.partSize; const total = Math.ceil(task.file.size / partSize);
      const done = new Set(status.completedParts); const partProgress = new Map<number, number>();
      mutate(task.id, { status: "uploading", progress: task.file.size ? done.size * partSize / task.file.size : 0, error: undefined });
      let cursor = 0;
      const worker = async () => {
        while (true) {
          while (cursor < total && done.has(cursor + 1)) cursor++;
          if (cursor >= total) return;
          const part = ++cursor; const controller = new AbortController(); active.current.set(task.id, controller);
          const from = (part - 1) * partSize, to = Math.min(from + partSize, task.file.size);
          await filesApi.uploadPart(uploadId!, part, task.file.slice(from, to), controller.signal, (fraction) => {
            partProgress.set(part, fraction); const bytes = done.size * partSize + [...partProgress.values()].reduce((a, b) => a + b * partSize, 0);
            const elapsed = Math.max((Date.now() - started) / 1000, 0.1); const speed = bytes / elapsed;
            mutate(task.id, { progress: Math.min(bytes / task.file.size, 0.999), speed, eta: speed ? Math.max(0, (task.file.size - bytes) / speed) : 0 });
          });
          active.current.delete(task.id); done.add(part); partProgress.delete(part);
          const bytes = Math.min(done.size * partSize, task.file.size); const elapsed = Math.max((Date.now() - started) / 1000, 0.1); const speed = bytes / elapsed;
          mutate(task.id, { progress: bytes / task.file.size, speed, eta: speed ? Math.max(0, (task.file.size - bytes) / speed) : 0 });
        }
      };
      await Promise.all(Array.from({ length: Math.min(3, total) }, worker));
      await filesApi.completeUpload(uploadId);
      mutate(task.id, { status: "complete", progress: 1, speed: 0, eta: 0 });
    } catch (error) {
      const paused = error instanceof DOMException && error.name === "AbortError";
      mutate(task.id, paused ? { status: "paused" } : { status: "error", error: error instanceof Error ? error.message : "Upload failed" });
    } finally { active.current.delete(task.id); busyFiles.current--; pumpRef.current(); }
  }, []);
  pumpRef.current = () => {
    if (busyFiles.current >= 2) return;
    const next = tasksRef.current.find((task) => task.status === "queued");
    if (next) void run(next);
  };
  useEffect(() => { pumpRef.current(); }, [tasks, run]);
  useEffect(() => {
    let live = true;
    void (async () => {
      try {
        const pending = await (await import("@/lib/api")).apiRequest<Array<{ id: string; fileName: string; folderId: string; sizeBytes: number; uploadId: string; completedParts: number[] }>>("/api/v1/uploads/resumable");
        if (!live || pending.length === 0) return;
        setTasks((current) => {
          const existing = new Set(current.map((task) => task.uploadId));
          return [...current, ...pending.filter((item) => !existing.has(item.uploadId)).map((item) => ({ id: item.id, uploadId: item.uploadId, folderId: item.folderId, file: new File([], item.fileName), status: "paused" as const, progress: 0, speed: 0, eta: 0, error: "Choose the original file to resume." }))];
        });
      } catch { /* The upload service may not be available while offline. */ }
    })();
    return () => { live = false; };
  }, []);
  const addFiles = (files: FileList | File[], folderId: string) => setTasks((current) => [...current, ...Array.from(files).map((file) => ({ id: crypto.randomUUID(), file, folderId, status: "queued" as const, progress: 0, speed: 0, eta: 0 }))]);
  const pause = (id: string) => active.current.get(id)?.abort();
  const resume = (id: string) => {
    const task = tasksRef.current.find((item) => item.id === id); if (!task) return;
    if (!task.file.size) return;
    mutate(id, { status: "queued", error: undefined });
  };
  const cancel = async (id: string) => {
    const task = tasksRef.current.find((item) => item.id === id); active.current.get(id)?.abort();
    if (task?.uploadId) { try { await filesApi.cancelUpload(task.uploadId); } catch { /* Keep the task removable even when server cancellation fails. */ } }
    setTasks((items) => items.filter((item) => item.id !== id));
  };
  const retry = (id: string) => mutate(id, { status: "queued", error: undefined });
  return { tasks, addFiles, pause, resume, cancel, retry };
}
