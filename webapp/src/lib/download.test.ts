import { afterEach, expect, it, vi } from "vitest";

import { saveBlobFile, saveTextFile } from "./download";

// jsdom has no object URLs; each case stubs them and the originals come back after it.
const originals = {
	createObjectURL: Object.getOwnPropertyDescriptor(URL, "createObjectURL"),
	revokeObjectURL: Object.getOwnPropertyDescriptor(URL, "revokeObjectURL"),
};
afterEach(() => {
	vi.useRealTimers();
	vi.restoreAllMocks();
	for (const [name, descriptor] of Object.entries(originals)) {
		Reflect.deleteProperty(URL, name);
		if (descriptor !== undefined) {
			Object.defineProperty(URL, name, descriptor);
		}
	}
});

/** Records each file handed to the browser and each anchor clicked, as the click found it. */
function captureDownloads() {
	const created: Blob[] = [];
	const revoked: string[] = [];
	const clicks: { href: string; download: string; attached: boolean; revokedBefore: string[] }[] =
		[];
	URL.createObjectURL = (blob: Blob) => {
		created.push(blob);
		return `blob:file-${created.length}`;
	};
	URL.revokeObjectURL = (url: string) => {
		revoked.push(url);
	};
	vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(
		function record(this: HTMLAnchorElement) {
			clicks.push({
				href: this.href,
				download: this.download,
				attached: this.isConnected,
				revokedBefore: [...revoked],
			});
		},
	);
	return { created, revoked, clicks };
}

it("clicks an anchor in the page to the file, and lets go of its address only well after the click", async () => {
	vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
	const { created, revoked, clicks } = captureDownloads();
	const file = new Blob(['{"datasetId":"d-1"}'], { type: "application/json" });

	saveBlobFile(file, "hephaestus-research-d-1.json");

	// The file goes to the browser as it was given, under the name asked for.
	expect(created).toHaveLength(1);
	expect(created[0]).toBe(file);
	expect(clicks).toStrictEqual([
		{
			href: "blob:file-1",
			download: "hephaestus-research-d-1.json",
			attached: true,
			revokedBefore: [],
		},
	]);
	expect(document.body.querySelector("a")).toBeNull();
	// The browser reads the file after the click, in a task of its own: the address is still there.
	await vi.advanceTimersByTimeAsync(1000);
	expect(revoked).toStrictEqual([]);
	// Nothing says when a download is done, so the address is revoked once, on a timer.
	await vi.runAllTimersAsync();
	expect(revoked).toStrictEqual(["blob:file-1"]);
});

it("saves text as a file of its type through the same download", async () => {
	vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
	const { created, revoked, clicks } = captureDownloads();

	saveTextFile("a,b\n1,2\n", "audit-log-2026-10-21.csv", "text/csv;charset=utf-8;");

	expect(clicks).toStrictEqual([
		{
			href: "blob:file-1",
			download: "audit-log-2026-10-21.csv",
			attached: true,
			revokedBefore: [],
		},
	]);
	expect(created.map(({ size, type }) => ({ size, type }))).toStrictEqual([
		{ size: 8, type: "text/csv;charset=utf-8;" },
	]);
	await vi.runAllTimersAsync();
	expect(revoked).toStrictEqual(["blob:file-1"]);
});
