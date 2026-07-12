import type {
	ActivityItem,
	AreaHealth,
	ArtifactRef,
	DeveloperPracticeProfile,
	PracticeAreaId,
} from "./types";

/**
 * A realistic six-person corpus mirroring the live-tested backend shapes:
 * - Priya: heavy contributor, many findings across security, testing and error handling,
 *   zero code review activity. Top of the needs-attention triage.
 * - Jonas: clean low-volume developer. Three observations, all good.
 * - Mara: mid developer, improving in testing while commit hygiene declines.
 * - Tomas: new developer with first-cycle activity only.
 * - Aisha, Denis: unremarkable middles, which is what most of a roster looks like.
 * All names, logins and repos are invented.
 */

// ---------------------------------------------------------------------------
// Artifacts
// ---------------------------------------------------------------------------

function pr(repo: string, number: number, title: string, state: ArtifactRef["state"]): ArtifactRef {
	return {
		kind: "PULL_REQUEST",
		number,
		title,
		repo,
		url: `https://github.com/${repo}/pull/${number}`,
		state,
	};
}

function issue(
	repo: string,
	number: number,
	title: string,
	state: ArtifactRef["state"],
): ArtifactRef {
	return {
		kind: "ISSUE",
		number,
		title,
		repo,
		url: `https://github.com/${repo}/issues/${number}`,
		state,
	};
}

const PAYMENTS = "nimbus/payments-api";
const CHECKOUT = "nimbus/checkout-service";
const STOREFRONT = "nimbus/web-storefront";
const INFRA = "nimbus/infra-tools";

const priyaWebhookRetries = pr(PAYMENTS, 482, "Add retry handling to payment webhooks", "MERGED");
const priyaAddressForms = pr(PAYMENTS, 476, "Parse customer address forms server side", "OPEN");
const priyaCartTotals = pr(CHECKOUT, 490, "Refactor cart totals rounding", "MERGED");
const priyaSignatureFailures = pr(PAYMENTS, 488, "Handle webhook signature failures", "OPEN");
const priyaSessionLookup = pr(CHECKOUT, 493, "Speed up checkout session lookup", "OPEN");
const priyaRetryIssue = issue(
	PAYMENTS,
	471,
	"Webhook retries silently stop after provider timeout",
	"OPEN",
);

const maraFareCalculator = pr(
	STOREFRONT,
	512,
	"Fix fare calculator rounding for split payments",
	"MERGED",
);
const maraMixedPr = pr(
	STOREFRONT,
	517,
	"Update product gallery, tweak search debounce, bump lodash",
	"OPEN",
);
const maraReviewedPr = pr(CHECKOUT, 505, "Extract discount engine into its own module", "MERGED");
const maraTimeoutHandling = pr(
	STOREFRONT,
	508,
	"Retry search requests on gateway timeouts",
	"MERGED",
);

const jonasOnboardingGuide = pr(
	STOREFRONT,
	133,
	"Update onboarding guide for the new billing flow",
	"MERGED",
);
const jonasCurrencyTest = pr(
	STOREFRONT,
	135,
	"Add regression test for currency formatting",
	"MERGED",
);

const tomasHealthCheck = pr(INFRA, 21, "Add health check endpoint to the deploy runner", "OPEN");
const tomasRetryIssue = issue(
	INFRA,
	19,
	"Deploy runner: clarify retry behaviour on partial failure",
	"OPEN",
);

const aishaReviewedPr = pr(
	CHECKOUT,
	498,
	"Introduce idempotency keys for order creation",
	"MERGED",
);
const aishaCouponTests = pr(STOREFRONT, 502, "Cover coupon stacking rules with tests", "MERGED");
const aishaTokenScopes = pr(PAYMENTS, 468, "Narrow token scopes for the refund worker", "MERGED");

const denisCacheTuning = pr(
	CHECKOUT,
	450,
	"Profile checkout hot path before tuning the cache",
	"MERGED",
);
const denisNamingPass = pr(CHECKOUT, 462, "Rename ambiguous cart mutation helpers", "OPEN");
const denisDepBump = pr(INFRA, 33, "Bump postgres driver and pin transitive versions", "MERGED");

// ---------------------------------------------------------------------------
// Developer profiles
// ---------------------------------------------------------------------------

export const PRIYA: DeveloperPracticeProfile = {
	login: "priya-r",
	name: "Priya Raghavan",
	avatarUrl: "https://i.pravatar.cc/64?img=47",
	needsAttention: true,
	attentionSummary:
		"Repeated findings across security, testing and error handling. No code review activity this cycle.",
	signals: [
		{
			practiceSlug: "secret-handling",
			practiceName: "Secrets stay out of the codebase",
			areaId: "security",
			status: "DEVELOPING",
			trend: "WORSENING",
			observationCount: 7,
			history: [0, 1, 1, 2, 3],
			latestEvidence: {
				artifact: priyaWebhookRetries,
				observedAt: "2026-07-09T14:32:00Z",
				reasoning:
					"A live provider key was committed in a test fixture and had to be rotated after review.",
			},
			guidance:
				"Load provider keys from the environment in test fixtures, then add the fixture path to the secret scanner allowlist check.",
		},
		{
			practiceSlug: "input-validation",
			practiceName: "Untrusted input is validated at the boundary",
			areaId: "security",
			status: "DEVELOPING",
			trend: "STEADY",
			observationCount: 5,
			history: [1, 1, 1, 1, 1],
			latestEvidence: {
				artifact: priyaAddressForms,
				observedAt: "2026-07-08T09:12:00Z",
				reasoning: "Address form fields reach the query layer without server side validation.",
			},
			guidance:
				"Validate the address payload with the existing schema helpers before it leaves the controller.",
		},
		{
			practiceSlug: "tests-cover-changes",
			practiceName: "Tests cover the changed behaviour",
			areaId: "testing",
			status: "DEVELOPING",
			trend: "STEADY",
			observationCount: 9,
			history: [2, 2, 1, 2, 2],
			latestEvidence: {
				artifact: priyaCartTotals,
				observedAt: "2026-07-07T16:45:00Z",
				reasoning: "The rounding change shipped without a test for the new half-cent edge case.",
			},
			guidance:
				"Add one test that pins the half-cent rounding case before the next change to cart totals.",
		},
		{
			practiceSlug: "deterministic-tests",
			practiceName: "Tests are deterministic",
			areaId: "testing",
			status: "MIXED",
			trend: "IMPROVING",
			observationCount: 4,
			history: [2, 1, 1, 0, 0],
			latestEvidence: {
				artifact: priyaSessionLookup,
				observedAt: "2026-07-06T11:20:00Z",
				reasoning:
					"The new session lookup tests use a fixed clock instead of sleeping, which removes the old flake pattern.",
			},
		},
		{
			practiceSlug: "failures-surface-context",
			practiceName: "Failures surface with context",
			areaId: "error-handling",
			status: "DEVELOPING",
			trend: "WORSENING",
			observationCount: 6,
			history: [0, 1, 1, 2, 2],
			latestEvidence: {
				artifact: priyaSignatureFailures,
				observedAt: "2026-07-10T08:05:00Z",
				reasoning:
					"Webhook signature errors are swallowed by a bare catch, so retries never trigger and the failure is invisible.",
			},
			guidance:
				"Let the signature error propagate to the retry handler and log the webhook id with it.",
		},
		{
			practiceSlug: "focused-commits",
			practiceName: "Commits stay focused",
			areaId: "commit-hygiene",
			status: "MIXED",
			trend: "STEADY",
			observationCount: 8,
			history: [2, 2, 1, 2, 1],
			latestEvidence: {
				artifact: priyaCartTotals,
				observedAt: "2026-07-07T16:45:00Z",
				reasoning: "The rounding refactor landed as one focused commit with a clear message.",
			},
		},
		{
			practiceSlug: "pr-descriptions-explain-intent",
			practiceName: "Pull requests explain their intent",
			areaId: "pr-craft",
			status: "STRENGTH",
			trend: "STEADY",
			observationCount: 10,
			history: [2, 2, 2, 2, 2],
			latestEvidence: {
				artifact: priyaSessionLookup,
				observedAt: "2026-07-11T10:30:00Z",
				reasoning:
					"The description explains the latency problem, the approach and what reviewers should focus on.",
			},
		},
		{
			practiceSlug: "review-comments-explain-why",
			practiceName: "Review comments explain why",
			areaId: "constructive-code-review",
			status: "NO_ACTIVITY",
			trend: "STEADY",
			observationCount: 0,
			history: [0, 0, 0, 0, 0],
		},
	],
};

export const JONAS: DeveloperPracticeProfile = {
	login: "jweber",
	name: "Jonas Weber",
	avatarUrl: "https://i.pravatar.cc/64?img=12",
	needsAttention: false,
	signals: [
		{
			practiceSlug: "docs-move-with-code",
			practiceName: "Docs move with the code",
			areaId: "documentation",
			status: "STRENGTH",
			trend: "STEADY",
			observationCount: 1,
			history: [0, 0, 1, 0, 0],
			latestEvidence: {
				artifact: jonasOnboardingGuide,
				observedAt: "2026-06-30T13:00:00Z",
				reasoning:
					"The onboarding guide was updated in the same change that altered the billing flow it describes.",
			},
		},
		{
			practiceSlug: "tests-cover-changes",
			practiceName: "Tests cover the changed behaviour",
			areaId: "testing",
			status: "STRENGTH",
			trend: "NEW",
			observationCount: 1,
			history: [0, 0, 0, 0, 1],
			latestEvidence: {
				artifact: jonasCurrencyTest,
				observedAt: "2026-07-08T10:15:00Z",
				reasoning:
					"The formatting fix arrived together with a regression test that reproduces the original bug.",
			},
		},
		{
			practiceSlug: "focused-commits",
			practiceName: "Commits stay focused",
			areaId: "commit-hygiene",
			status: "STRENGTH",
			trend: "STEADY",
			observationCount: 1,
			history: [0, 0, 1, 0, 0],
			latestEvidence: {
				artifact: jonasCurrencyTest,
				observedAt: "2026-07-08T10:15:00Z",
				reasoning: "Two commits, each doing one thing, each with a message that says why.",
			},
		},
	],
};

export const MARA: DeveloperPracticeProfile = {
	login: "mlindqvist",
	name: "Mara Lindqvist",
	avatarUrl: "https://i.pravatar.cc/64?img=32",
	needsAttention: true,
	attentionSummary: "Commit hygiene declining since last cycle. Testing is improving.",
	signals: [
		{
			practiceSlug: "tests-cover-changes",
			practiceName: "Tests cover the changed behaviour",
			areaId: "testing",
			status: "MIXED",
			trend: "IMPROVING",
			observationCount: 6,
			history: [0, 1, 1, 2, 2],
			latestEvidence: {
				artifact: maraFareCalculator,
				observedAt: "2026-07-09T15:40:00Z",
				reasoning: "The rounding fix came with regression tests written before the fix itself.",
			},
		},
		{
			practiceSlug: "focused-commits",
			practiceName: "Commits stay focused",
			areaId: "commit-hygiene",
			status: "MIXED",
			trend: "WORSENING",
			observationCount: 7,
			history: [1, 1, 2, 1, 2],
			latestEvidence: {
				artifact: maraMixedPr,
				observedAt: "2026-07-10T12:20:00Z",
				reasoning: "Three unrelated changes landed in one commit, which would make a revert risky.",
			},
			guidance:
				"Split the gallery change, the debounce tweak and the dependency bump into separate commits before merging.",
		},
		{
			practiceSlug: "review-comments-explain-why",
			practiceName: "Review comments explain why",
			areaId: "constructive-code-review",
			status: "STRENGTH",
			trend: "STEADY",
			observationCount: 5,
			history: [1, 1, 1, 1, 1],
			latestEvidence: {
				artifact: maraReviewedPr,
				observedAt: "2026-07-05T09:30:00Z",
				reasoning:
					"Review comments name the risk each suggestion removes instead of just requesting the change.",
			},
		},
		{
			practiceSlug: "failures-surface-context",
			practiceName: "Failures surface with context",
			areaId: "error-handling",
			status: "MIXED",
			trend: "STEADY",
			observationCount: 3,
			history: [1, 0, 1, 0, 1],
			latestEvidence: {
				artifact: maraTimeoutHandling,
				observedAt: "2026-07-03T14:10:00Z",
				reasoning:
					"Gateway timeouts are retried with backoff, but the retry exhaustion path drops the request id.",
			},
			guidance:
				"Carry the request id into the exhaustion log line so support can trace dropped searches.",
		},
	],
};

export const TOMAS: DeveloperPracticeProfile = {
	login: "tferreira",
	name: "Tomas Ferreira",
	avatarUrl: "https://i.pravatar.cc/64?img=68",
	needsAttention: false,
	signals: [
		{
			practiceSlug: "pr-descriptions-explain-intent",
			practiceName: "Pull requests explain their intent",
			areaId: "pr-craft",
			status: "MIXED",
			trend: "NEW",
			observationCount: 2,
			history: [0, 0, 0, 1, 1],
			latestEvidence: {
				artifact: tomasHealthCheck,
				observedAt: "2026-07-10T09:00:00Z",
				reasoning:
					"The description says what the endpoint does but not why the deploy runner needs it.",
			},
			guidance: "Add one sentence on the incident or need that motivated the endpoint.",
		},
		{
			practiceSlug: "tests-cover-changes",
			practiceName: "Tests cover the changed behaviour",
			areaId: "testing",
			status: "DEVELOPING",
			trend: "NEW",
			observationCount: 1,
			history: [0, 0, 0, 0, 1],
			latestEvidence: {
				artifact: tomasHealthCheck,
				observedAt: "2026-07-10T09:00:00Z",
				reasoning: "The new endpoint has no test for the unhealthy path it exists to report.",
			},
			guidance: "Add a test that makes a dependency unhealthy and asserts the endpoint reports it.",
		},
		{
			practiceSlug: "issues-state-expected-behaviour",
			practiceName: "Issues state expected behaviour",
			areaId: "issue-craft",
			status: "STRENGTH",
			trend: "NEW",
			observationCount: 1,
			history: [0, 0, 0, 0, 1],
			latestEvidence: {
				artifact: tomasRetryIssue,
				observedAt: "2026-07-11T08:30:00Z",
				reasoning:
					"The issue states observed behaviour, expected behaviour and reproduction steps in three short lines.",
			},
		},
	],
};

export const AISHA: DeveloperPracticeProfile = {
	login: "abello",
	name: "Aisha Bello",
	avatarUrl: "https://i.pravatar.cc/64?img=45",
	needsAttention: false,
	signals: [
		{
			practiceSlug: "review-comments-explain-why",
			practiceName: "Review comments explain why",
			areaId: "constructive-code-review",
			status: "STRENGTH",
			trend: "STEADY",
			observationCount: 8,
			history: [2, 1, 2, 2, 1],
			latestEvidence: {
				artifact: aishaReviewedPr,
				observedAt: "2026-07-08T11:00:00Z",
				reasoning:
					"The review walked through a failure scenario the idempotency change would have missed.",
			},
		},
		{
			practiceSlug: "tests-cover-changes",
			practiceName: "Tests cover the changed behaviour",
			areaId: "testing",
			status: "MIXED",
			trend: "STEADY",
			observationCount: 5,
			history: [1, 1, 1, 1, 1],
			latestEvidence: {
				artifact: aishaCouponTests,
				observedAt: "2026-07-06T10:30:00Z",
				reasoning:
					"Coupon stacking rules are covered, but the expiry interaction is still untested.",
			},
		},
		{
			practiceSlug: "least-privilege",
			practiceName: "Access follows least privilege",
			areaId: "security",
			status: "MIXED",
			trend: "IMPROVING",
			observationCount: 2,
			history: [0, 0, 1, 0, 1],
			latestEvidence: {
				artifact: aishaTokenScopes,
				observedAt: "2026-07-02T09:45:00Z",
				reasoning: "The refund worker token was narrowed from account-wide to refund-only scopes.",
			},
		},
		{
			practiceSlug: "docs-move-with-code",
			practiceName: "Docs move with the code",
			areaId: "documentation",
			status: "MIXED",
			trend: "STEADY",
			observationCount: 3,
			history: [1, 0, 1, 0, 1],
			latestEvidence: {
				artifact: aishaTokenScopes,
				observedAt: "2026-07-02T09:45:00Z",
				reasoning:
					"The scope change updated the runbook, but the architecture diagram still shows the old token.",
			},
		},
		{
			practiceSlug: "reviews-answered-promptly",
			practiceName: "Review requests get a timely response",
			areaId: "collaboration",
			status: "STRENGTH",
			trend: "STEADY",
			observationCount: 4,
			history: [1, 1, 1, 0, 1],
			latestEvidence: {
				artifact: aishaReviewedPr,
				observedAt: "2026-07-08T11:00:00Z",
				reasoning: "Review requests were picked up within a working day across the whole cycle.",
			},
		},
	],
};

export const DENIS: DeveloperPracticeProfile = {
	login: "dkovac",
	name: "Denis Kovac",
	avatarUrl: "https://i.pravatar.cc/64?img=59",
	needsAttention: false,
	signals: [
		{
			practiceSlug: "names-reveal-intent",
			practiceName: "Names reveal intent",
			areaId: "code-clarity",
			status: "MIXED",
			trend: "STEADY",
			observationCount: 6,
			history: [1, 2, 1, 1, 1],
			latestEvidence: {
				artifact: denisNamingPass,
				observedAt: "2026-07-09T13:15:00Z",
				reasoning:
					"The rename pass removes most ambiguity, though two helpers still share a near-identical name.",
			},
		},
		{
			practiceSlug: "measure-before-tuning",
			practiceName: "Hot paths are measured before tuning",
			areaId: "performance-awareness",
			status: "STRENGTH",
			trend: "IMPROVING",
			observationCount: 3,
			history: [0, 1, 0, 1, 1],
			latestEvidence: {
				artifact: denisCacheTuning,
				observedAt: "2026-07-01T15:00:00Z",
				reasoning: "The cache change includes before and after profiles instead of a guess.",
			},
		},
		{
			practiceSlug: "tests-cover-changes",
			practiceName: "Tests cover the changed behaviour",
			areaId: "testing",
			status: "MIXED",
			trend: "STEADY",
			observationCount: 4,
			history: [1, 1, 0, 1, 1],
			latestEvidence: {
				artifact: denisNamingPass,
				observedAt: "2026-07-09T13:15:00Z",
				reasoning:
					"The rename kept existing coverage green, but the new helper overloads are untested.",
			},
		},
		{
			practiceSlug: "deliberate-dependency-updates",
			practiceName: "Dependencies are updated deliberately",
			areaId: "dependency-care",
			status: "MIXED",
			trend: "STEADY",
			observationCount: 2,
			history: [0, 1, 0, 0, 1],
			latestEvidence: {
				artifact: denisDepBump,
				observedAt: "2026-07-04T10:00:00Z",
				reasoning:
					"The driver bump pins transitive versions, but the changelog of the new major went unreviewed.",
			},
			guidance:
				"Link the driver changelog in the description and call out anything that affects connection pooling.",
		},
		{
			practiceSlug: "focused-commits",
			practiceName: "Commits stay focused",
			areaId: "commit-hygiene",
			status: "STRENGTH",
			trend: "STEADY",
			observationCount: 5,
			history: [1, 1, 1, 1, 1],
			latestEvidence: {
				artifact: denisDepBump,
				observedAt: "2026-07-04T10:00:00Z",
				reasoning: "The bump and the pinning are separate commits, each explained.",
			},
		},
	],
};

/** The full six-person roster, triage order: needs-attention first, then alphabetical. */
export const TEAM_PROFILES: readonly DeveloperPracticeProfile[] = [
	PRIYA,
	MARA,
	AISHA,
	DENIS,
	JONAS,
	TOMAS,
];

// ---------------------------------------------------------------------------
// Activity feeds (candidate A)
// ---------------------------------------------------------------------------

export const PRIYA_FEED: readonly ActivityItem[] = [
	{
		id: "priya-493",
		artifact: priyaSessionLookup,
		happenedAt: "2026-07-11T10:30:00Z",
		observations: [
			{
				practiceName: "Pull requests explain their intent",
				areaId: "pr-craft",
				tone: "POSITIVE",
				reasoning:
					"The description explains the latency problem, the approach and what reviewers should focus on.",
			},
			{
				practiceName: "Tests are deterministic",
				areaId: "testing",
				tone: "POSITIVE",
				reasoning:
					"The new session lookup tests use a fixed clock instead of sleeping, which removes the old flake pattern.",
			},
		],
	},
	{
		id: "priya-488",
		artifact: priyaSignatureFailures,
		happenedAt: "2026-07-10T08:05:00Z",
		observations: [
			{
				practiceName: "Failures surface with context",
				areaId: "error-handling",
				tone: "ATTENTION",
				reasoning:
					"Webhook signature errors are swallowed by a bare catch, so retries never trigger and the failure is invisible.",
				guidance:
					"Let the signature error propagate to the retry handler and log the webhook id with it.",
			},
		],
	},
	{
		id: "priya-482",
		artifact: priyaWebhookRetries,
		happenedAt: "2026-07-09T14:32:00Z",
		observations: [
			{
				practiceName: "Secrets stay out of the codebase",
				areaId: "security",
				tone: "ATTENTION",
				reasoning:
					"A live provider key was committed in a test fixture and had to be rotated after review.",
				guidance:
					"Load provider keys from the environment in test fixtures, then add the fixture path to the secret scanner allowlist check.",
			},
		],
	},
	{
		id: "priya-476",
		artifact: priyaAddressForms,
		happenedAt: "2026-07-08T09:12:00Z",
		observations: [
			{
				practiceName: "Untrusted input is validated at the boundary",
				areaId: "security",
				tone: "ATTENTION",
				reasoning: "Address form fields reach the query layer without server side validation.",
				guidance:
					"Validate the address payload with the existing schema helpers before it leaves the controller.",
			},
		],
	},
	{
		id: "priya-490",
		artifact: priyaCartTotals,
		happenedAt: "2026-07-07T16:45:00Z",
		observations: [
			{
				practiceName: "Tests cover the changed behaviour",
				areaId: "testing",
				tone: "ATTENTION",
				reasoning: "The rounding change shipped without a test for the new half-cent edge case.",
				guidance:
					"Add one test that pins the half-cent rounding case before the next change to cart totals.",
			},
			{
				practiceName: "Commits stay focused",
				areaId: "commit-hygiene",
				tone: "POSITIVE",
				reasoning: "The rounding refactor landed as one focused commit with a clear message.",
			},
		],
	},
	{
		id: "priya-471",
		artifact: priyaRetryIssue,
		happenedAt: "2026-07-05T11:00:00Z",
		observations: [],
	},
];

export const MARA_FEED: readonly ActivityItem[] = [
	{
		id: "mara-517",
		artifact: maraMixedPr,
		happenedAt: "2026-07-10T12:20:00Z",
		observations: [
			{
				practiceName: "Commits stay focused",
				areaId: "commit-hygiene",
				tone: "ATTENTION",
				reasoning: "Three unrelated changes landed in one commit, which would make a revert risky.",
				guidance:
					"Split the gallery change, the debounce tweak and the dependency bump into separate commits before merging.",
			},
		],
	},
	{
		id: "mara-512",
		artifact: maraFareCalculator,
		happenedAt: "2026-07-09T15:40:00Z",
		observations: [
			{
				practiceName: "Tests cover the changed behaviour",
				areaId: "testing",
				tone: "POSITIVE",
				reasoning: "The rounding fix came with regression tests written before the fix itself.",
			},
		],
	},
	{
		id: "mara-505",
		artifact: maraReviewedPr,
		happenedAt: "2026-07-05T09:30:00Z",
		observations: [
			{
				practiceName: "Review comments explain why",
				areaId: "constructive-code-review",
				tone: "POSITIVE",
				reasoning:
					"Review comments name the risk each suggestion removes instead of just requesting the change.",
			},
		],
	},
	{
		id: "mara-508",
		artifact: maraTimeoutHandling,
		happenedAt: "2026-07-03T14:10:00Z",
		observations: [
			{
				practiceName: "Failures surface with context",
				areaId: "error-handling",
				tone: "ATTENTION",
				reasoning:
					"Gateway timeouts are retried with backoff, but the retry exhaustion path drops the request id.",
				guidance:
					"Carry the request id into the exhaustion log line so support can trace dropped searches.",
			},
		],
	},
];

export const TOMAS_FEED: readonly ActivityItem[] = [
	{
		id: "tomas-19",
		artifact: tomasRetryIssue,
		happenedAt: "2026-07-11T08:30:00Z",
		observations: [
			{
				practiceName: "Issues state expected behaviour",
				areaId: "issue-craft",
				tone: "POSITIVE",
				reasoning:
					"The issue states observed behaviour, expected behaviour and reproduction steps in three short lines.",
			},
		],
	},
	{
		id: "tomas-21",
		artifact: tomasHealthCheck,
		happenedAt: "2026-07-10T09:00:00Z",
		observations: [
			{
				practiceName: "Tests cover the changed behaviour",
				areaId: "testing",
				tone: "ATTENTION",
				reasoning: "The new endpoint has no test for the unhealthy path it exists to report.",
				guidance:
					"Add a test that makes a dependency unhealthy and asserts the endpoint reports it.",
			},
			{
				practiceName: "Pull requests explain their intent",
				areaId: "pr-craft",
				tone: "ATTENTION",
				reasoning:
					"The description says what the endpoint does but not why the deploy runner needs it.",
				guidance: "Add one sentence on the incident or need that motivated the endpoint.",
			},
		],
	},
];

export const JONAS_FEED: readonly ActivityItem[] = [
	{
		id: "jonas-135",
		artifact: jonasCurrencyTest,
		happenedAt: "2026-07-08T10:15:00Z",
		observations: [
			{
				practiceName: "Tests cover the changed behaviour",
				areaId: "testing",
				tone: "POSITIVE",
				reasoning:
					"The formatting fix arrived together with a regression test that reproduces the original bug.",
			},
			{
				practiceName: "Commits stay focused",
				areaId: "commit-hygiene",
				tone: "POSITIVE",
				reasoning: "Two commits, each doing one thing, each with a message that says why.",
			},
		],
	},
	{
		id: "jonas-133",
		artifact: jonasOnboardingGuide,
		happenedAt: "2026-06-30T13:00:00Z",
		observations: [
			{
				practiceName: "Docs move with the code",
				areaId: "documentation",
				tone: "POSITIVE",
				reasoning:
					"The onboarding guide was updated in the same change that altered the billing flow it describes.",
			},
		],
	},
];

export const FEEDS_BY_LOGIN: Readonly<Record<string, readonly ActivityItem[]>> = {
	"priya-r": PRIYA_FEED,
	mlindqvist: MARA_FEED,
	tferreira: TOMAS_FEED,
	jweber: JONAS_FEED,
	abello: [],
	dkovac: [],
};

// ---------------------------------------------------------------------------
// Workspace health
// ---------------------------------------------------------------------------

function health(
	areaId: PracticeAreaId,
	developing: number,
	mixed: number,
	strength: number,
): AreaHealth {
	return { areaId, availability: "AVAILABLE", developing, mixed, strength };
}

/** Filled workspace: most areas have signal, two are below the reporting threshold. */
export const WORKSPACE_HEALTH_FILLED: readonly AreaHealth[] = [
	health("constructive-code-review", 0, 0, 2),
	health("testing", 2, 3, 1),
	health("security", 2, 1, 0),
	health("error-handling", 1, 1, 0),
	health("documentation", 0, 1, 1),
	health("issue-craft", 0, 0, 1),
	health("commit-hygiene", 0, 2, 2),
	health("pr-craft", 0, 1, 1),
	{ areaId: "collaboration", availability: "SUPPRESSED" },
	{ areaId: "code-clarity", availability: "SUPPRESSED" },
	{ areaId: "performance-awareness", availability: "NO_DATA" },
	{ areaId: "dependency-care", availability: "NO_DATA" },
];

/** Below the member threshold: every area is suppressed, personal data stays visible. */
export const WORKSPACE_HEALTH_SUPPRESSED: readonly AreaHealth[] = WORKSPACE_HEALTH_FILLED.map(
	(area) => ({ areaId: area.areaId, availability: "SUPPRESSED" as const }),
);

/** Fresh workspace: nothing observed yet anywhere. */
export const WORKSPACE_HEALTH_SPARSE: readonly AreaHealth[] = WORKSPACE_HEALTH_FILLED.map(
	(area) => ({ areaId: area.areaId, availability: "NO_DATA" as const }),
);

// ---------------------------------------------------------------------------
// Sparse workspace (first days after connecting a repo)
// ---------------------------------------------------------------------------

export const SPARSE_PROFILES: readonly DeveloperPracticeProfile[] = [
	TOMAS,
	{
		login: "nkim",
		name: "Nadia Kim",
		avatarUrl: "https://i.pravatar.cc/64?img=25",
		needsAttention: false,
		signals: [],
	},
];

export const SPARSE_FEEDS_BY_LOGIN: Readonly<Record<string, readonly ActivityItem[]>> = {
	tferreira: TOMAS_FEED,
	nkim: [],
};

// ---------------------------------------------------------------------------
// Large roster (scannability check at 30 developers)
// ---------------------------------------------------------------------------

const EXTRA_NAMES: readonly (readonly [string, string])[] = [
	["Lena Hoffmann", "lhoffmann"],
	["Marco Ricci", "mricci"],
	["Sofia Petrova", "spetrova"],
	["Ethan Caldwell", "ecaldwell"],
	["Yuki Tanaka", "ytanaka"],
	["Omar Haddad", "ohaddad"],
	["Ingrid Solberg", "isolberg"],
	["Pavel Novak", "pnovak"],
	["Camille Roux", "croux"],
	["Diego Fuentes", "dfuentes"],
	["Hana Kowalski", "hkowalski"],
	["Ravi Menon", "rmenon"],
	["Elif Aydin", "eaydin"],
	["Bram De Vries", "bdevries"],
	["Nora Lindgren", "nlindgren"],
	["Stefan Barta", "sbarta"],
	["Amara Osei", "aosei"],
	["Luca Moretti", "lmoretti"],
	["Freya Nielsen", "fnielsen"],
	["Viktor Horvath", "vhorvath"],
	["Zainab Rahman", "zrahman"],
	["Oscar Blanco", "oblanco"],
	["Mette Dahl", "mdahl"],
	["Ilya Sorokin", "isorokin"],
];

/**
 * Deterministically expands the six-person corpus to `count` developers by rotating the base
 * profiles under new identities. Only the six named profiles keep triage flags, so the large
 * roster stays a realistic "mostly fine, a few need support" distribution.
 */
export function buildLargeRoster(count: number): DeveloperPracticeProfile[] {
	const roster: DeveloperPracticeProfile[] = [...TEAM_PROFILES];
	for (let i = 0; roster.length < count && i < EXTRA_NAMES.length; i++) {
		const base = TEAM_PROFILES[(i + 2) % TEAM_PROFILES.length];
		const [name, login] = EXTRA_NAMES[i];
		roster.push({
			login,
			name,
			avatarUrl: `https://i.pravatar.cc/64?img=${(i * 7) % 70}`,
			needsAttention: false,
			attentionSummary: undefined,
			signals: base.signals,
		});
	}
	return roster;
}
