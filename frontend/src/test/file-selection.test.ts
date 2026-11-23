import { describe, expect, it } from "vitest";
import { toggleSelection, type VaultItem } from "@/lib/files";

const items = ["a", "b", "c", "d"].map((id) => ({ id, name: id, kind: "file" as const, sizeBytes: 1, mime: "text/plain", createdAt: "", updatedAt: "" })) as VaultItem[];
describe("file selection", () => {
  it("selects a single row and toggles it off", () => {
    expect(toggleSelection([], "b", { index: 1, items }).selected).toEqual(["b"]);
    expect(toggleSelection(["b"], "b", { index: 1, items }).selected).toEqual([]);
  });
  it("supports additive and shift range selection", () => {
    expect(toggleSelection(["a"], "c", { index: 2, items, additive: true }).selected).toEqual(["a", "c"]);
    expect(toggleSelection(["a"], "d", { index: 3, items, shift: true, anchor: 0 }).selected).toEqual(["a", "b", "c", "d"]);
  });
});
