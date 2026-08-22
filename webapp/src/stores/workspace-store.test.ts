import { afterEach, assert, beforeEach, describe, expect, it } from "vitest";
import { isRecord } from "@/lib/is-record";
import { useWorkspaceStore } from "./workspace-store";

const STORAGE_KEY = "hephaestus-workspace-selection";

describe("workspace-store", () => {
	beforeEach(() => {
		localStorage.clear();
		useWorkspaceStore.setState({ selectedSlug: undefined, hasHydrated: false });
	});

	afterEach(() => {
		localStorage.clear();
		useWorkspaceStore.setState({ selectedSlug: undefined, hasHydrated: false });
	});

	it("persists the selected workspace slug in localStorage", () => {
		useWorkspaceStore.getState().setSelectedSlug("prompt-edu");

		const written = localStorage.getItem(STORAGE_KEY);
		assert(written, "Nothing was written under the storage key.");
		const persisted: unknown = JSON.parse(written);
		assert(isRecord(persisted), "The persisted value is not an object.");

		expect(persisted.state).toStrictEqual({ selectedSlug: "prompt-edu" });
	});

	it("rehydrates the selected workspace slug from localStorage", async () => {
		localStorage.setItem(
			STORAGE_KEY,
			JSON.stringify({
				state: { selectedSlug: "prompt-edu" },
				version: 0,
			}),
		);

		await useWorkspaceStore.persist.rehydrate();

		expect(useWorkspaceStore.getState().selectedSlug).toBe("prompt-edu");
		expect(useWorkspaceStore.getState().hasHydrated).toBe(true);
	});
});
