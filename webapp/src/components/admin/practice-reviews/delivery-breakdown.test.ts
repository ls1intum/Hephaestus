import { describe, expect, it } from "vitest";

import { deliveredByChannel, withheldByReason } from "./delivery-breakdown";
import { deliveredByChannelCounts, feedbackCounts } from "./fixtures";

const SCOPE = { from: "2026-09-10", to: "2026-10-09" };
/** The scope as a Feedback link carries it: every key, the ones it does not narrow by empty. */
const SEARCH = { ...SCOPE, practiceSlug: undefined, recipientUserId: undefined };

describe("deliveredByChannel", () => {
	it("counts delivered and partly delivered feedback per channel, in the Channel filter's order", () => {
		const rows = deliveredByChannel(deliveredByChannelCounts, SCOPE);
		expect(rows.map((row) => [row.def.label, row.count])).toStrictEqual([
			["On GitHub", 8],
			["With Heph in Slack", 2],
			["On their practice pages", 4],
		]);
		expect(rows[0]?.target).toStrictEqual({
			list: "feedback",
			search: {
				...SEARCH,
				deliveryState: ["DELIVERED", "PARTIALLY_DELIVERED"],
				location: ["GITHUB"],
			},
		});
	});

	it("leaves out a channel that delivered nothing, whatever else it holds", () => {
		const rows = deliveredByChannel(
			[{ location: "GITLAB", feedback: { ...feedbackCounts([]), failed: 3 } }],
			SCOPE,
		);
		expect(rows).toStrictEqual([]);
	});
});

describe("withheldByReason", () => {
	it("groups the reasons as the Why withheld filter does, then the rejected", () => {
		const rows = withheldByReason(
			[
				{ reason: "ARTIFACT_MERGED", count: 2 },
				{ reason: "ARTIFACT_CLOSED", count: 1 },
				{ reason: "RECIPIENT_OPTED_OUT", count: 1 },
			],
			{ discarded: 2 },
			SCOPE,
		);
		expect(rows.map((row) => [row.key, row.def.label, row.count])).toStrictEqual([
			["WORK_MOVED_ON", "The work moved on", 3],
			["DEVELOPER_CHOICE", "The developer’s choice", 1],
			["DISCARDED", "Rejected", 2],
		]);
		expect(rows[0]?.target?.search).toStrictEqual({
			...SEARCH,
			deliveryState: ["SUPPRESSED"],
			withheldFamily: ["WORK_MOVED_ON"],
		});
		expect(rows[2]?.target?.search).toStrictEqual({ ...SEARCH, deliveryState: ["DISCARDED"] });
	});

	it("names the withheld feedback without a recorded reason, without a link", () => {
		const rows = withheldByReason(
			[{ reason: "VOLUME_CAPPED", count: 1 }, { count: 2 }],
			{ discarded: 0 },
			SCOPE,
		);
		expect(rows.at(-1)).toMatchObject({ key: "UNRECORDED", count: 2 });
		expect(rows.at(-1)?.target).toBeUndefined();
	});
});
