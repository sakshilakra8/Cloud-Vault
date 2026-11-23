import { describe, expect, it } from "vitest";
import { optimisticallyUpdateItems, type VaultItem } from "@/lib/files";
import { transitionUploadTask, type UploadTask } from "@/hooks/use-upload-queue";

const items = ["file-1", "folder-1"].map((id) => ({ id, name: id, kind: id.startsWith("folder") ? "folder" as const : "file" as const, sizeBytes: 10, mime: "text/plain", createdAt: "", updatedAt: "" })) as VaultItem[];
describe("optimistic file mutations", () => {
  it("renames the target while retaining other rows", () => {
    expect(optimisticallyUpdateItems(items, { type: "rename", ids: ["file-1"], name: "renamed.txt" }).map((item) => item.name)).toEqual(["renamed.txt", "folder-1"]);
  });
  it("removes moved or trashed rows from the current folder cache", () => {
    expect(optimisticallyUpdateItems(items, { type: "move", ids: ["file-1"] })).toEqual([items[1]]);
    expect(optimisticallyUpdateItems(items, { type: "trash", ids: ["folder-1"] })).toEqual([items[0]]);
  });
});
describe("upload queue transitions", () => {
  it("keeps progress and moves an upload from active to paused and back to queued", () => {
    const task = { id: "upload-1", file: new File(["data"], "a.txt"), folderId: "root", status: "uploading", progress: 0.4, speed: 100, eta: 5 } as UploadTask;
    const paused = transitionUploadTask([task], task.id, { status: "paused" });
    expect(paused[0]).toMatchObject({ status: "paused", progress: 0.4 });
    expect(transitionUploadTask(paused, task.id, { status: "queued" })[0].status).toBe("queued");
  });
});
