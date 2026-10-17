import { describe, expect, it } from "vitest";

import { statusValues } from "@/components/common/status-def";

import { REMOVED_FILE_DEF, SCAN_ENTRY_DEFS, SCAN_ENTRY_STATUSES } from "./scan-entry-defs";

describe("scan entry vocabulary", () => {
	it("words each report item status for a reader, not as the wire constant", () => {
		expect(SCAN_ENTRY_STATUSES.map((status) => SCAN_ENTRY_DEFS[status].label)).toStrictEqual([
			"Reviewed",
			"Reused",
			"Withheld",
			"Waiting",
			"Not reviewed",
		]);
	});

	it("gives every status, and a removed file, its own icon", () => {
		const icons = [
			...statusValues(SCAN_ENTRY_DEFS).map((status) => SCAN_ENTRY_DEFS[status].icon),
			REMOVED_FILE_DEF.icon,
		];
		expect(new Set(icons).size).toBe(icons.length);
		expect(REMOVED_FILE_DEF.label).toBe("Removed");
	});
});
