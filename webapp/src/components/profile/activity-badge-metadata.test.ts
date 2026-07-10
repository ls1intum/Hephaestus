import { describe, expect, it } from "vitest";
import { type ActivityBadgeKey, getActivityBadgeMetadata } from "./activity-badge-metadata";

const ORDER: readonly ActivityBadgeKey[] = [
	"changeRequests",
	"approvals",
	"comments",
	"codeComments",
	"ownReplies",
	"openPullRequests",
	"mergedPullRequests",
	"closedPullRequests",
	"openedIssues",
	"closedIssues",
];

describe("getActivityBadgeMetadata", () => {
	it("returns all badges in render order for GitHub", () => {
		const keys = getActivityBadgeMetadata("GITHUB").map((b) => b.key);
		expect(keys).toEqual(ORDER);
	});

	it("returns all badges in render order for GitLab", () => {
		const keys = getActivityBadgeMetadata("GITLAB").map((b) => b.key);
		expect(keys).toEqual(ORDER);
	});

	it("marks the first four badges as review activity and leaves the rest as context", () => {
		const flags = getActivityBadgeMetadata("GITHUB").map((b) => b.primaryReviewSignal);
		expect(flags).toEqual([true, true, true, true, false, false, false, false, false, false]);
	});

	it("threads provider terminology through labels and tooltips", () => {
		const github = getActivityBadgeMetadata("GITHUB").find((b) => b.key === "openPullRequests");
		const gitlab = getActivityBadgeMetadata("GITLAB").find((b) => b.key === "openPullRequests");
		expect(github?.label).toBe("Open pull requests");
		expect(gitlab?.label).toBe("Open merge requests");
		expect(gitlab?.tooltip).toContain("merge requests");
	});

	it("interpolates the count argument into ariaLabel", () => {
		const approvals = getActivityBadgeMetadata("GITHUB").find((b) => b.key === "approvals");
		expect(approvals?.ariaLabel(7)).toContain("7");
		expect(approvals?.ariaLabel(42)).toContain("42");
	});
});
