import { HttpResponse, http } from "msw";
import type {
	AreaHealth,
	AreaStatusCell,
	PracticeReportCard,
	PracticeReportItem,
	PracticeReportSummary,
	PracticeStatus,
	PracticeTrend,
} from "@/components/practices/practice-types";

/**
 * Story fixtures for the practice surfaces, shaped exactly like the generated API types and
 * keyed on the seeded practice-area catalogue. All names, logins and repositories are
 * invented. Timestamps are relative to now so relative times and sparklines always render the
 * way they would live.
 */

function daysAgo(days: number): Date {
	return new Date(Date.now() - days * 24 * 60 * 60 * 1000);
}

// ---------------------------------------------------------------------------
// The seeded areas (slug + name), in catalogue display order.
// ---------------------------------------------------------------------------

export const AREAS: ReadonlyArray<{ slug: string; name: string }> = [
	{ slug: "review-ready-work", name: "Packaging work for review" },
	{ slug: "acting-on-review-feedback", name: "Acting on review feedback" },
	{ slug: "actionable-issue-authoring", name: "Writing issues a maintainer can act on" },
	{ slug: "constructive-code-review", name: "Reviewing a teammate's work constructively" },
	{ slug: "testing-discipline", name: "Testing your changes" },
	{ slug: "code-craftsmanship", name: "Writing maintainable code" },
	{ slug: "robust-error-handling", name: "Handling failure well" },
	{ slug: "secure-by-default-changes", name: "Making changes secure by default" },
	{ slug: "decisions-and-documentation", name: "Recording decisions and documenting changes" },
	{
		slug: "delivery-and-version-control-discipline",
		name: "Disciplined delivery and version control",
	},
	{ slug: "issue-traceability-and-lifecycle", name: "Tracking and planning the work" },
	{ slug: "communication", name: "Communicating in the open" },
];

// ---------------------------------------------------------------------------
// Report items (evidence rows), each anchored to a concrete PR or issue.
// ---------------------------------------------------------------------------

let itemSequence = 0;

interface ItemOptions {
	guidance?: string;
	severity?: PracticeReportItem["severity"];
	artifactType?: PracticeReportItem["artifactType"];
	artifactState?: PracticeReportItem["artifactState"];
	locator?: string;
	daysAgo?: number;
}

function item(
	title: string,
	repo: string,
	number: number,
	artifactTitle: string,
	options: ItemOptions = {},
): PracticeReportItem {
	itemSequence += 1;
	const kind = options.artifactType ?? "PULL_REQUEST";
	const segment = kind === "ISSUE" ? "issues" : "pull";
	return {
		observationId: `00000000-0000-0000-0000-${String(itemSequence).padStart(12, "0")}`,
		title,
		guidance: options.guidance,
		severity: options.severity,
		artifactType: kind,
		artifactId: number,
		locator: options.locator,
		observedAt: daysAgo(options.daysAgo ?? 3),
		artifactTitle,
		artifactUrl: `https://github.com/${repo}/${segment}/${number}`,
		artifactNumber: number,
		artifactRepository: repo,
		artifactState: options.artifactState ?? "OPEN",
	};
}

const PAYMENTS = "nimbus/payments-api";
const CHECKOUT = "nimbus/checkout-service";
const STOREFRONT = "nimbus/web-storefront";

// ---------------------------------------------------------------------------
// The developer self view: report cards.
// ---------------------------------------------------------------------------

function card(
	slug: string,
	name: string,
	areaSlug: string,
	status: PracticeReportCard["status"],
	trend: PracticeTrend,
	toWorkOn: PracticeReportItem[],
	strengths: PracticeReportItem[] = [],
): PracticeReportCard {
	const area = AREAS.find((candidate) => candidate.slug === areaSlug);
	return {
		slug,
		name,
		areaSlug,
		areaName: area?.name,
		status,
		trend,
		toWorkOn,
		strengths,
	};
}

/** A filled self report: three clear focus candidates, strengths, and quieter practices. */
export const MY_REPORT_CARDS: PracticeReportCard[] = [
	card(
		"ships-tests-with-the-change",
		"Include tests with the change",
		"testing-discipline",
		"DEVELOPING",
		"WORSENING",
		[
			item(
				"Retry handling for payment webhooks ships with no test",
				PAYMENTS,
				482,
				"Add retry handling to payment webhooks",
				{
					guidance: "Add a regression test for the retry path before extending it further.",
					severity: "MAJOR",
					artifactState: "MERGED",
					locator: "src/webhooks/retry.ts:88",
					daysAgo: 2,
				},
			),
			item(
				"Cart totals rounding change lands without covering the new branch",
				CHECKOUT,
				490,
				"Refactor cart totals rounding",
				{ severity: "MINOR", artifactState: "MERGED", daysAgo: 9 },
			),
		],
	),
	card(
		"handles-failure-paths-deliberately",
		"Handle failure paths deliberately",
		"robust-error-handling",
		"DEVELOPING",
		"STEADY",
		[
			item(
				"Webhook signature failures fall through to a silent catch",
				PAYMENTS,
				488,
				"Handle webhook signature failures",
				{
					guidance: "Log and surface the failure so operators can see dropped webhooks.",
					severity: "CRITICAL",
					locator: "src/webhooks/verify.ts:41",
					daysAgo: 4,
				},
			),
		],
	),
	card(
		"describe-what-and-why",
		"Describe what changed and why",
		"review-ready-work",
		"MIXED",
		"WORSENING",
		[
			item(
				"The gallery update mixes three unrelated concerns in one PR",
				STOREFRONT,
				517,
				"Update product gallery, tweak search debounce, bump lodash",
				{
					guidance: "Split unrelated changes so each review stays focused on one concern.",
					severity: "MINOR",
					daysAgo: 5,
				},
			),
		],
		[
			item(
				"The fare calculator fix explains the rounding bug and links the report",
				STOREFRONT,
				512,
				"Fix fare calculator rounding for split payments",
				{ artifactState: "MERGED", daysAgo: 12 },
			),
		],
	),
	card(
		"leaves-useful-specific-review-comments",
		"Leave specific, actionable review comments",
		"constructive-code-review",
		"MIXED",
		"IMPROVING",
		[
			item(
				"A review approval without reading the migration in the diff",
				CHECKOUT,
				505,
				"Extract discount engine into its own module",
				{ severity: "MINOR", artifactState: "MERGED", daysAgo: 16 },
			),
		],
		[
			item(
				"Concrete, kind review guidance on the idempotency change",
				CHECKOUT,
				498,
				"Introduce idempotency keys for order creation",
				{ artifactState: "MERGED", daysAgo: 6 },
			),
		],
	),
	card(
		"issue-states-an-actionable-problem",
		"State a problem a maintainer can act on",
		"actionable-issue-authoring",
		"STRENGTH",
		"IMPROVING",
		[],
		[
			item(
				"The webhook retry issue states the problem, the impact and a reproduction",
				PAYMENTS,
				471,
				"Webhook retries silently stop after provider timeout",
				{ artifactType: "ISSUE", daysAgo: 8 },
			),
		],
	),
	card(
		"removes-duplication-instead-of-copy-pasting",
		"Factor out duplication instead of copy-pasting",
		"code-craftsmanship",
		"STRENGTH",
		"STEADY",
		[],
		[
			item(
				"The session lookup change reuses the existing cache helper",
				CHECKOUT,
				493,
				"Speed up checkout session lookup",
				{ daysAgo: 10 },
			),
		],
	),
	card(
		"commit-subjects-explain-each-change",
		"Write commit subjects a reviewer can follow",
		"delivery-and-version-control-discipline",
		"MIXED",
		"STEADY",
		[
			item(
				'Four commits titled "fix" in one branch',
				STOREFRONT,
				508,
				"Retry search requests on gateway timeouts",
				{ severity: "INFO", artifactState: "MERGED", daysAgo: 20 },
			),
		],
		[
			item(
				"The rounding fix commits read as a reviewable story",
				STOREFRONT,
				512,
				"Fix fare calculator rounding for split payments",
				{ artifactState: "MERGED", daysAgo: 12 },
			),
		],
	),
];

/** A first-cycle self report: one practice observed so far, nothing needing focus. */
export const SPARSE_REPORT_CARDS: PracticeReportCard[] = [
	card(
		"describe-what-and-why",
		"Describe what changed and why",
		"review-ready-work",
		"STRENGTH",
		"NEW",
		[],
		[
			item(
				"The health check PR says what it adds and why the runner needs it",
				"nimbus/infra-tools",
				21,
				"Add health check endpoint to the deploy runner",
				{ daysAgo: 1 },
			),
		],
	),
];

// ---------------------------------------------------------------------------
// The mentor view: roster summaries + workspace health.
// ---------------------------------------------------------------------------

function areaCells(
	overrides: Record<string, { status: PracticeStatus; trend?: PracticeTrend }>,
): AreaStatusCell[] {
	return AREAS.map((area) => ({
		areaSlug: area.slug,
		areaName: area.name,
		status: overrides[area.slug]?.status ?? "NO_ACTIVITY",
		trend: overrides[area.slug]?.trend ?? "STEADY",
	}));
}

function developer(
	userId: number,
	userLogin: string,
	name: string,
	overrides: Record<string, { status: PracticeStatus; trend?: PracticeTrend }>,
	attentionReasons: string[] = [],
): PracticeReportSummary {
	return {
		userId,
		userLogin,
		name,
		avatarUrl: "",
		areas: areaCells(overrides),
		needsAttention: attentionReasons.length > 0,
		attentionReasons,
	};
}

/** Six developers in server triage order: needs-attention first, then by login. */
export const ROSTER: PracticeReportSummary[] = [
	developer(
		101,
		"priya-r",
		"Priya Raghavan",
		{
			"testing-discipline": { status: "DEVELOPING", trend: "WORSENING" },
			"robust-error-handling": { status: "DEVELOPING", trend: "STEADY" },
			"secure-by-default-changes": { status: "MIXED", trend: "WORSENING" },
			"review-ready-work": { status: "MIXED", trend: "STEADY" },
			"code-craftsmanship": { status: "STRENGTH", trend: "STEADY" },
		},
		[
			"Testing your changes: gaps to work on this cycle",
			"Handling failure well: gaps to work on this cycle",
		],
	),
	developer(
		102,
		"mara-k",
		"Mara Kovacs",
		{
			"delivery-and-version-control-discipline": { status: "DEVELOPING", trend: "STEADY" },
			"testing-discipline": { status: "MIXED", trend: "IMPROVING" },
			"review-ready-work": { status: "STRENGTH", trend: "STEADY" },
		},
		["Disciplined delivery and version control: gaps to work on this cycle"],
	),
	developer(103, "aisha-o", "Aisha Okafor", {
		"constructive-code-review": { status: "STRENGTH", trend: "STEADY" },
		"testing-discipline": { status: "STRENGTH", trend: "STEADY" },
		"secure-by-default-changes": { status: "STRENGTH", trend: "IMPROVING" },
	}),
	developer(104, "denis-b", "Denis Baranov", {
		"code-craftsmanship": { status: "MIXED", trend: "STEADY" },
		communication: { status: "STRENGTH", trend: "STEADY" },
	}),
	developer(105, "jonas-w", "Jonas Weber", {
		"decisions-and-documentation": { status: "STRENGTH", trend: "STEADY" },
		"testing-discipline": { status: "STRENGTH", trend: "STEADY" },
	}),
	developer(106, "tomas-l", "Tomas Lindgren", {
		"review-ready-work": { status: "STRENGTH", trend: "NEW" },
		"actionable-issue-authoring": { status: "MIXED", trend: "NEW" },
	}),
];

/** A two-person, first-cycle roster. */
export const SPARSE_ROSTER: PracticeReportSummary[] = [
	developer(201, "tomas-l", "Tomas Lindgren", {
		"review-ready-work": { status: "STRENGTH", trend: "NEW" },
	}),
	developer(202, "jonas-w", "Jonas Weber", {
		"actionable-issue-authoring": { status: "MIXED", trend: "NEW" },
	}),
];

/** A thirty-person roster for the scannability test. */
export function buildLargeRoster(size = 30): PracticeReportSummary[] {
	const statuses: PracticeStatus[] = ["STRENGTH", "MIXED", "STRENGTH", "DEVELOPING", "NO_ACTIVITY"];
	const trends: PracticeTrend[] = ["STEADY", "IMPROVING", "STEADY", "WORSENING", "STEADY", "NEW"];
	return Array.from({ length: size }, (_, index) => {
		const overrides: Record<string, { status: PracticeStatus; trend?: PracticeTrend }> = {};
		for (const [areaIndex, area] of AREAS.entries()) {
			const status = statuses[(index + areaIndex) % statuses.length];
			if (status !== "NO_ACTIVITY") {
				overrides[area.slug] = { status, trend: trends[(index + areaIndex) % trends.length] };
			}
		}
		const needsAttention = index % 7 === 3;
		return developer(
			300 + index,
			`dev-${index + 1}`,
			`Developer ${index + 1}`,
			overrides,
			needsAttention ? ["Testing your changes: gaps to work on this cycle"] : [],
		);
	}).sort((a, b) => Number(b.needsAttention ?? false) - Number(a.needsAttention ?? false));
}

function health(
	areaSlug: string,
	availability: AreaHealth["availability"],
	counts: Partial<
		Pick<AreaHealth, "strengthCount" | "mixedCount" | "developingCount" | "noActivityCount">
	> = {},
): AreaHealth {
	const area = AREAS.find((candidate) => candidate.slug === areaSlug);
	return {
		areaSlug,
		areaName: area?.name ?? areaSlug,
		availability,
		...counts,
	};
}

/** Full counts on every area, as an admin sees them. */
export const HEALTH_FILLED: AreaHealth[] = AREAS.map((area, index) =>
	health(area.slug, "AVAILABLE", {
		strengthCount: 3 + (index % 3),
		mixedCount: 1 + (index % 2),
		developingCount: index % 3,
	}),
);

/** Every area below the privacy threshold. */
export const HEALTH_SUPPRESSED: AreaHealth[] = AREAS.map((area) => health(area.slug, "SUPPRESSED"));

/** A fresh workspace: two areas with early counts, the rest without activity. */
export const HEALTH_SPARSE: AreaHealth[] = AREAS.map((area, index) =>
	index < 2
		? health(area.slug, "AVAILABLE", { strengthCount: 1, mixedCount: 1 })
		: health(area.slug, "NO_DATA"),
);

// ---------------------------------------------------------------------------
// MSW handlers for the query-connected page containers.
// ---------------------------------------------------------------------------

export const myReportHandler = http.get("*/workspaces/:slug/practices/reports/me", () =>
	HttpResponse.json(MY_REPORT_CARDS),
);

export const sparseMyReportHandler = http.get("*/workspaces/:slug/practices/reports/me", () =>
	HttpResponse.json(SPARSE_REPORT_CARDS),
);

export const emptyMyReportHandler = http.get("*/workspaces/:slug/practices/reports/me", () =>
	HttpResponse.json([]),
);

export const myReportErrorHandler = http.get("*/workspaces/:slug/practices/reports/me", () =>
	HttpResponse.json({ title: "Internal Server Error", status: 500 }, { status: 500 }),
);

export const rosterHandler = http.get("*/workspaces/:slug/practices/reports", () =>
	HttpResponse.json(ROSTER),
);

export const healthHandler = http.get("*/workspaces/:slug/practices/health", () =>
	HttpResponse.json(HEALTH_FILLED),
);

export const suppressedHealthHandler = http.get("*/workspaces/:slug/practices/health", () =>
	HttpResponse.json(HEALTH_SUPPRESSED),
);

export const rosterErrorHandler = http.get("*/workspaces/:slug/practices/reports", () =>
	HttpResponse.json({ title: "Internal Server Error", status: 500 }, { status: 500 }),
);

export const drillDownHandler = http.get("*/workspaces/:slug/practices/reports/:userId", () =>
	HttpResponse.json(MY_REPORT_CARDS),
);
