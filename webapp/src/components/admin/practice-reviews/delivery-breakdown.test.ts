import { describe, expect, it } from "vitest";

import { deliveredByLocation, withheldByReason } from "./delivery-breakdown";
import { deliveredByLocationCounts } from "./fixtures";

const SCOPE = { from: "2026-09-10", to: "2026-10-09" };
/** The scope as a Feedback link carries it: every key, the ones it does not narrow by empty. */
const SEARCH = { ...SCOPE, practiceSlug: undefined, developerId: undefined };

describe("deliveredByLocation", () => {
	it("counts delivered feedback per location, in the Location filter's order", () => {
		const rows = deliveredByLocation(deliveredByLocationCounts, SCOPE);
		expect(rows.map((row) => [row.key, row.count])).toStrictEqual([
			["GITHUB", 8],
			["SLACK", 2],
			["PRACTICE_PAGES", 4],
		]);
		expect(rows[0]?.target).toStrictEqual({
			list: "feedback",
			search: { ...SEARCH, state: ["DELIVERED"], location: "GITHUB" },
		});
	});

	it("leaves out a location that delivered nothing", () => {
		const rows = deliveredByLocation(
			[{ reason: "GITLAB", description: "On GitLab", count: 0 }],
			SCOPE,
		);
		expect(rows).toStrictEqual([]);
	});
});

describe("withheldByReason", () => {
	it("groups the reasons as the Why withheld filter does, then the rejected", () => {
		const rows = withheldByReason(
			[
				{ reason: "ITEM_MERGED", description: "", count: 2 },
				{ reason: "ITEM_CLOSED", description: "", count: 1 },
				{ reason: "DELIVERY_OFF", description: "", count: 1 },
			],
			{ rejected: 2 },
			SCOPE,
		);
		expect(rows.map((row) => [row.key, row.def.label, row.count])).toStrictEqual([
			["WORK_MOVED_ON", "The work moved on", 3],
			["DEVELOPER_CHOICE", "The developer’s choice", 1],
			["REJECTED", "Rejected", 2],
		]);
		expect(rows[0]?.target?.search).toStrictEqual({
			...SEARCH,
			state: ["WITHHELD"],
			withheldFamily: ["WORK_MOVED_ON"],
		});
		expect(rows[2]?.target?.search).toStrictEqual({ ...SEARCH, state: ["REJECTED"] });
	});

	it("names the withheld feedback whose reason it does not know, without a link", () => {
		const rows = withheldByReason(
			[
				{ reason: "VOLUME_CAPPED", description: "", count: 1 },
				{ reason: "SOMETHING_NEW", description: "", count: 2 },
			],
			{ rejected: 0 },
			SCOPE,
		);
		expect(rows.at(-1)).toMatchObject({ key: "UNKNOWN", count: 2 });
		expect(rows.at(-1)?.target).toBeUndefined();
	});
});
