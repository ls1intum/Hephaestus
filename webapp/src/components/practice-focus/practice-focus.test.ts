import { describe, expect, it } from "vitest";

import { parseDetailStack } from "@/components/layout/detail-drawer/detail-stack";

import {
	ALL_GROUPS_FOCUS,
	focusOfProfileStack,
	focusSwitchHistory,
	type PracticeFocus,
	profileStackOf,
	workspaceSearchOf,
} from "./practice-focus";

const GROUP = { kind: "practice-group", id: "review-ready-work" };
const PRACTICE = { kind: "practice", id: "scope-to-one-concern" };
const groupOf = (slug: string) => (slug === PRACTICE.id ? GROUP.id : undefined);

describe("the focus of the Practice profile", () => {
	it("is the group of a group level and the practice over its group", () => {
		expect(focusOfProfileStack([GROUP], groupOf)).toStrictEqual({ groupSlug: GROUP.id });
		expect(focusOfProfileStack([GROUP, PRACTICE], groupOf)).toStrictEqual({
			groupSlug: GROUP.id,
			practiceSlug: PRACTICE.id,
		});
	});

	it("finds a practice's group when the practice opened alone, and has none for a practice in no group", () => {
		expect(focusOfProfileStack([PRACTICE], groupOf)).toStrictEqual({
			groupSlug: GROUP.id,
			practiceSlug: PRACTICE.id,
		});
		expect(focusOfProfileStack([{ kind: "practice", id: "loose" }], groupOf)).toBeUndefined();
	});

	it("reads the front level only: the list of every group is every group, a review has none", () => {
		expect(focusOfProfileStack([], groupOf)).toBeUndefined();
		expect(focusOfProfileStack([{ kind: "practice-groups", id: "all" }], groupOf)).toStrictEqual(
			ALL_GROUPS_FOCUS,
		);
		expect(
			focusOfProfileStack([GROUP, PRACTICE, { kind: "review", id: "r1" }], groupOf),
		).toBeUndefined();
		expect(
			focusOfProfileStack([{ kind: "practice-groups", id: "all" }, GROUP], groupOf),
		).toStrictEqual({ groupSlug: GROUP.id });
	});
});

/**
 * One switch to the workspace page and one back by the marked practice's row, read off the addresses
 * each page writes: the workspace page reads its group level and the practice it marks.
 */
function roundTrip(focus: PracticeFocus | undefined): PracticeFocus | undefined {
	if (focus === undefined) {
		return undefined;
	}
	const workspace = workspaceSearchOf(focus);
	const [group] = parseDetailStack(workspace.detail, ["practice-group"]);
	if (group === undefined) {
		return undefined;
	}
	const back = { groupSlug: group.id, practiceSlug: workspace.practice };
	return focusOfProfileStack(profileStackOf(back), groupOf);
}

function roundTrips(start: PracticeFocus, count: number): PracticeFocus | undefined {
	let focus: PracticeFocus | undefined = start;
	for (let trip = 0; trip < count; trip += 1) {
		focus = roundTrip(focus);
	}
	return focus;
}

const START: PracticeFocus = { groupSlug: GROUP.id, practiceSlug: PRACTICE.id };

describe("a switch between the two pages", () => {
	it("keeps the focus through any number of round trips", () => {
		const trips = roundTrips(START, 20);
		expect(trips).toStrictEqual(START);
	});

	it("opens at most a group and a practice in the profile, and one group level on the workspace page", () => {
		expect(profileStackOf(START)).toHaveLength(2);
		expect(profileStackOf({ groupSlug: GROUP.id })).toHaveLength(1);
		expect(workspaceSearchOf(START)).toStrictEqual({
			detail: ["practice-group:review-ready-work"],
			practice: PRACTICE.id,
		});
	});

	it("opens every group as the profile's list of them and the workspace page with no level", () => {
		expect(profileStackOf(ALL_GROUPS_FOCUS)).toStrictEqual([
			{ kind: "practice-groups", id: "all" },
		]);
		expect(workspaceSearchOf(ALL_GROUPS_FOCUS)).toStrictEqual({
			detail: undefined,
			practice: undefined,
		});
		expect(focusOfProfileStack(profileStackOf(ALL_GROUPS_FOCUS), groupOf)).toStrictEqual(
			ALL_GROUPS_FOCUS,
		);
	});

	it("adds one history entry from a page reached on its own, then replaces it", () => {
		expect(focusSwitchHistory({})).toMatchObject({ replace: false });
		expect(focusSwitchHistory(undefined)).toMatchObject({ replace: false });
		const written = focusSwitchHistory({}).state;
		expect(focusSwitchHistory(written)).toMatchObject({ replace: true });
		// The level the switch opened is not one this visit pushed, so closing it does not go back.
		expect(written.detailPush).toBe(false);
	});
});
