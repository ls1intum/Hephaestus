import type {
	Practice,
	ReviewArtifact,
	ReviewFeedback,
	ReviewFeedbackDetail,
	ReviewObservation,
	ReviewObservationDetail,
	ReviewRunSummary,
	ReviewTierAssignment,
	WorkspaceMembership,
} from "@/api/types.gen";

/**
 * Four pieces of work, one per kind the product reviews.
 *
 * Every list and every detail screen draws its icon from the provider and its words from the kind, so
 * a fixture set of four GitHub pull requests would let a wrong glyph or a missing label ship. These
 * are the four the artifact registry knows about, each on its real provider.
 */
export const reviewArtifact: ReviewArtifact = {
	id: 42,
	type: "scm.pull_request",
	provider: "GITHUB",
	number: 1423,
	repositoryName: "ls1intum/Hephaestus",
	title: "Show operators what a review observed and where its feedback went",
	url: "https://github.com/ls1intum/Hephaestus/pull/1423",
};

export const gitlabMergeRequest: ReviewArtifact = {
	id: 43,
	type: "scm.pull_request",
	provider: "GITLAB",
	number: 88,
	repositoryName: "platform/billing-service",
	title: "Move invoice numbering behind the billing boundary",
	url: "https://gitlab.example.com/platform/billing-service/-/merge_requests/88",
};

export const slackConversation: ReviewArtifact = {
	id: 81,
	type: "chat.conversation_thread",
	provider: "SLACK",
	channelName: "engineering",
	title: "How should we roll back the pricing migration?",
	url: "https://example.slack.com/archives/C01/p1721400000",
};

export const outlineDocument: ReviewArtifact = {
	id: 96,
	type: "docs.document",
	provider: "OUTLINE",
	title: "Runbook: restoring a workspace from backup",
	url: "https://docs.example.com/doc/runbook-restore",
};

const ada = { id: 7, login: "ada", name: "Ada Lovelace" };
const grace = { id: 9, login: "grace", name: "Grace Hopper" };
const alan = { id: 11, login: "alan", name: "Alan Turing" };

export const workspaceMembers: WorkspaceMembership[] = [
	{ userId: ada.id, userLogin: ada.login, userName: ada.name, role: "MEMBER" },
	{ userId: grace.id, userLogin: grace.login, userName: grace.name, role: "ADMIN" },
	{ userId: alan.id, userLogin: alan.login, userName: alan.name, role: "MEMBER" },
];

/**
 * A practice as the catalogue holds it, with only the fields a fixture varies spelled out.
 *
 * The wire type carries a dozen governance fields that no review surface reads; repeating them in
 * every fixture would bury the three that matter here. `whyItMatters` and `whatGoodLooksLike` are the
 * prose `PracticeDetailHoverCard` shows, and a fixture without them renders the bare link — the story
 * would pass while proving nothing.
 */
function practiceFixture(
	practice: Pick<
		Practice,
		"id" | "slug" | "name" | "areaSlug" | "criteria" | "whyItMatters" | "whatGoodLooksLike"
	> & { displayOrder: number; tier: ReviewTierAssignment["effective"] },
): Practice {
	const { tier, ...rest } = practice;
	return {
		...rest,
		artifactKind: "scm.pull_request",
		createdAt: new Date("2026-01-01T00:00:00Z"),
		updatedAt: new Date("2026-06-01T00:00:00Z"),
		bindings: [],
		reviewTier: { effective: tier, inherited: false, source: "PRACTICE" },
		automatedReviewPolicy: {
			automatedReview: {
				evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
				mode: "LANGUAGE_MODEL",
			},
			knownLimitations: [],
			sourceContractVersion: "1.0.0",
			whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
		},
		automatedReviewValidation: {
			policyDigest: "sha256:0",
			reviewRuleFingerprint: "rule:1",
			sourceContractVersion: "1.0.0",
			status: "AUTHOR_DECLARED",
		},
	};
}

export const workspacePractices: Practice[] = [
	practiceFixture({
		id: 1,
		slug: "thin-controllers",
		name: "Thin controllers",
		areaSlug: "code-quality",
		criteria: "A controller validates its input, delegates, and maps the result. Nothing else.",
		whyItMatters:
			"Logic that lives in a controller can only be tested through HTTP, so it tends not to be tested at all.",
		whatGoodLooksLike:
			"The controller method reads as a list of three steps and names a service that does the work.",
		displayOrder: 0,
		tier: "PROPOSE",
	}),
	practiceFixture({
		id: 2,
		slug: "product-language",
		name: "Product language",
		areaSlug: "architecture",
		criteria: "Names in the code are the names the people using it would use.",
		whyItMatters:
			"A boundary named after its storage stops matching the product the day the storage changes, and every reader after that has to translate.",
		whatGoodLooksLike:
			"A route, a class or a column could be read aloud in a planning meeting without explanation.",
		displayOrder: 1,
		tier: "DELIVER",
	}),
];

export const practiceAreas = [
	{
		id: 1,
		slug: "code-quality",
		name: "Code quality",
		active: true,
		displayOrder: 0,
		createdAt: new Date("2026-01-01T00:00:00Z"),
	},
	{
		id: 2,
		slug: "architecture",
		name: "Architecture",
		active: true,
		displayOrder: 1,
		createdAt: new Date("2026-01-01T00:00:00Z"),
	},
];

const codeQuality = { slug: "code-quality", name: "Code quality" };
const architecture = { slug: "architecture", name: "Architecture" };

/**
 * Six observations spanning every conclusion the model can reach and every origin it can have.
 *
 * The list rows collapse presence and assessment into one badge and one leading icon, and hide the
 * origin badge for the ordinary case — none of which a fixture of two strengths would exercise.
 */
export const reviewObservations = [
	{
		id: "55555555-5555-5555-5555-555555555555",
		agentJobId: "11111111-1111-1111-1111-111111111111",
		artifact: reviewArtifact,
		area: codeQuality,
		assessment: "GOOD",
		claimCurrentness: "CURRENT",
		confidence: 0.94,
		feedbackDisposition: { delivered: 1, failed: 0, prepared: 0, superseded: 0, suppressed: 0 },
		observedAt: new Date("2026-07-28T13:40:00Z"),
		origin: "LIVE",
		practiceName: "Thin controllers",
		practiceSlug: "thin-controllers",
		presence: "PRESENT",
		subject: ada,
		title: "The controller hands review queries straight to a service",
	},
	{
		id: "66666666-6666-6666-6666-666666666666",
		agentJobId: "11111111-1111-1111-1111-111111111111",
		artifact: reviewArtifact,
		area: architecture,
		assessment: "BAD",
		claimCurrentness: "CURRENT",
		confidence: 0.87,
		feedbackDisposition: { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 1 },
		observedAt: new Date("2026-07-28T13:38:00Z"),
		origin: "LIVE",
		practiceName: "Product language",
		practiceSlug: "product-language",
		presence: "PRESENT",
		severity: "MAJOR",
		subject: grace,
		title: "The route is named after a pipeline stage rather than the task",
	},
	{
		id: "77777777-7777-7777-7777-777777777777",
		agentJobId: "22222222-2222-2222-2222-222222222222",
		artifact: gitlabMergeRequest,
		area: architecture,
		assessment: "BAD",
		claimCurrentness: "STALE",
		confidence: 0.71,
		feedbackDisposition: { delivered: 0, failed: 0, prepared: 1, superseded: 0, suppressed: 0 },
		observedAt: new Date("2026-07-27T09:15:00Z"),
		// A campaign's observation, so the surface can be seen distinguishing it from a live one.
		origin: "BACKFILL",
		practiceName: "Product language",
		practiceSlug: "product-language",
		presence: "PRESENT",
		severity: "CRITICAL",
		subject: alan,
		title: "Invoice numbering leaks the ledger's table name into the public API",
	},
	{
		id: "88888888-8888-8888-8888-888888888888",
		agentJobId: "22222222-2222-2222-2222-222222222222",
		artifact: gitlabMergeRequest,
		area: codeQuality,
		claimCurrentness: "CURRENT",
		confidence: 0.62,
		feedbackDisposition: { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 0 },
		observedAt: new Date("2026-07-27T09:14:00Z"),
		origin: "LIVE",
		practiceName: "Thin controllers",
		practiceSlug: "thin-controllers",
		presence: "NOT_APPLICABLE",
		subject: alan,
		title: "The change touches no controller",
	},
	{
		id: "99999999-9999-9999-9999-999999999999",
		agentJobId: "33333333-3333-3333-3333-333333333333",
		artifact: slackConversation,
		area: architecture,
		claimCurrentness: "UNVERIFIABLE",
		confidence: 0.4,
		feedbackDisposition: { delivered: 1, failed: 0, prepared: 0, superseded: 0, suppressed: 0 },
		observedAt: new Date("2026-07-26T16:02:00Z"),
		origin: "MANUAL",
		practiceName: "Product language",
		practiceSlug: "product-language",
		presence: "INCONCLUSIVE",
		subject: grace,
		title: "The thread never settles what the rollback would actually undo",
	},
	{
		id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
		agentJobId: "44444444-4444-4444-4444-444444444444",
		artifact: outlineDocument,
		area: codeQuality,
		assessment: "GOOD",
		claimCurrentness: "CURRENT",
		confidence: 0.91,
		feedbackDisposition: { delivered: 0, failed: 1, prepared: 0, superseded: 0, suppressed: 0 },
		observedAt: new Date("2026-07-25T11:30:00Z"),
		origin: "LIVE",
		practiceName: "Thin controllers",
		practiceSlug: "thin-controllers",
		presence: "PRESENT",
		subject: ada,
		title: "The runbook states the one command that has to be run first",
	},
] satisfies ReviewObservation[];

/**
 * Six pieces of feedback covering both delivery places and every outcome.
 *
 * The two conversation rows matter most: the outcome a row shows is refined by its place, so a
 * `DELIVERED` on the conversation lane reads "Delivered in conversation" and a `PREPARED` reads
 * "Queued for conversation". A fixture of on-the-work rows would show neither.
 */
export const reviewFeedback = [
	{
		id: "33333333-3333-3333-3333-333333333333",
		agentJobId: "11111111-1111-1111-1111-111111111111",
		artifact: reviewArtifact,
		bodyPreview:
			"You kept the controller focused and moved the query logic into a dedicated service.",
		bodyTruncated: false,
		channel: "IN_CONTEXT",
		createdAt: new Date("2026-07-28T13:42:00Z"),
		deliveredAt: new Date("2026-07-28T13:43:00Z"),
		deliveryState: "DELIVERED",
		observationCount: 1,
		recipient: ada,
		subject: ada,
	},
	{
		id: "44444444-4444-4444-4444-444444444444",
		agentJobId: "11111111-1111-1111-1111-111111111111",
		artifact: reviewArtifact,
		bodyPreview:
			"Consider naming the boundary after the product concept rather than the storage model.",
		bodyTruncated: false,
		channel: "IN_CONTEXT",
		createdAt: new Date("2026-07-28T13:41:00Z"),
		deliveryState: "SUPPRESSED",
		observationCount: 1,
		recipient: grace,
		subject: grace,
		suppressionReason: "ARTIFACT_MERGED",
	},
	{
		id: "55555555-4444-4444-4444-444444444444",
		agentJobId: "22222222-2222-2222-2222-222222222222",
		artifact: gitlabMergeRequest,
		bodyPreview:
			"The public API exposes a name from the ledger schema. Renaming it now costs one migration; later it costs every caller.",
		bodyTruncated: false,
		channel: "CONVERSATION",
		createdAt: new Date("2026-07-27T09:20:00Z"),
		deliveryState: "PREPARED",
		observationCount: 2,
		recipient: alan,
		subject: alan,
	},
	{
		id: "66666666-4444-4444-4444-444444444444",
		agentJobId: "33333333-3333-3333-3333-333333333333",
		artifact: slackConversation,
		bodyPreview:
			"When you next describe a rollback, say what state it returns the system to — the thread left that open.",
		bodyTruncated: false,
		channel: "CONVERSATION",
		createdAt: new Date("2026-07-26T16:05:00Z"),
		deliveredAt: new Date("2026-07-26T18:40:00Z"),
		deliveryState: "DELIVERED",
		observationCount: 1,
		recipient: grace,
		subject: grace,
	},
	{
		id: "77777777-4444-4444-4444-444444444444",
		artifact: outlineDocument,
		agentJobId: "44444444-4444-4444-4444-444444444444",
		bodyPreview:
			"The runbook opens with the command that has to run first, which is the right order.",
		bodyTruncated: false,
		channel: "IN_CONTEXT",
		createdAt: new Date("2026-07-25T11:32:00Z"),
		deliveryState: "FAILED",
		observationCount: 1,
		recipient: ada,
		subject: ada,
	},
	{
		id: "88888888-4444-4444-4444-444444444444",
		agentJobId: "11111111-1111-1111-1111-111111111111",
		artifact: reviewArtifact,
		bodyPreview: "An earlier version of the note about the controller boundary.",
		bodyTruncated: false,
		channel: "IN_CONTEXT",
		createdAt: new Date("2026-07-24T08:00:00Z"),
		deliveredAt: new Date("2026-07-24T08:01:00Z"),
		deliveryState: "SUPERSEDED",
		observationCount: 1,
		recipient: ada,
		subject: ada,
	},
] satisfies ReviewFeedback[];

export const reviewRuns = [
	{
		id: "11111111-1111-1111-1111-111111111111",
		status: "COMPLETED",
		target: reviewArtifact,
		createdAt: new Date("2026-07-28T13:35:00Z"),
		observations: { strengths: 2, problems: 1, notApplicable: 1, inconclusive: 1 },
		feedback: { delivered: 1, failed: 0, prepared: 0, superseded: 1, suppressed: 1 },
	},
	{
		id: "22222222-2222-2222-2222-222222222222",
		status: "COMPLETED",
		target: gitlabMergeRequest,
		createdAt: new Date("2026-07-27T09:10:00Z"),
		observations: { strengths: 0, problems: 1, notApplicable: 1, inconclusive: 0 },
		feedback: { delivered: 0, failed: 0, prepared: 1, superseded: 0, suppressed: 0 },
	},
	{
		id: "33333333-3333-3333-3333-333333333333",
		status: "RUNNING",
		target: slackConversation,
		createdAt: new Date("2026-07-26T16:00:00Z"),
		observations: { strengths: 0, problems: 0, notApplicable: 0, inconclusive: 0 },
		feedback: { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 0 },
	},
	{
		id: "44444444-4444-4444-4444-444444444444",
		status: "FAILED",
		target: outlineDocument,
		createdAt: new Date("2026-07-25T11:25:00Z"),
		observations: { strengths: 0, problems: 0, notApplicable: 0, inconclusive: 0 },
		feedback: { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 0 },
	},
] satisfies ReviewRunSummary[];

const withheldFeedback = reviewFeedback[1];
const shortfall = reviewObservations[1];

export const reviewFeedbackDetail: ReviewFeedbackDetail = {
	id: withheldFeedback.id,
	agentJobId: withheldFeedback.agentJobId,
	artifact: reviewArtifact,
	body: [
		"## What could improve",
		"",
		"Name the route after the operator's task, not an internal pipeline stage. Somebody opening",
		"this page is asking *what did the review see*, and the URL answers with the name of the step",
		"that produced it.",
		"",
		"- `…/detection-output` names the machinery",
		"- `…/observations` names the thing",
		"",
		"[The naming guideline](https://example.com/guide) has the longer version.",
	].join("\n"),
	channel: "IN_CONTEXT",
	createdAt: withheldFeedback.createdAt,
	deliveryState: "SUPPRESSED",
	observations: [
		{
			observationId: shortfall.id,
			area: shortfall.area,
			assessment: "BAD",
			claimCurrentness: "CURRENT",
			confidence: shortfall.confidence,
			observedAt: shortfall.observedAt,
			ordinal: 0,
			practiceName: shortfall.practiceName,
			practiceSlug: shortfall.practiceSlug,
			presence: "PRESENT",
			role: "PRIMARY",
			severity: "MAJOR",
			title: shortfall.title,
		},
	],
	placements: [],
	recipient: withheldFeedback.recipient,
	subject: withheldFeedback.subject,
	suppressionReason: "ARTIFACT_MERGED",
};

/**
 * An observation whose evidence spans three sources, two of them without meaningful line numbers.
 *
 * A single diff citation — which is what this fixture used to hold — exercises the one case the
 * evidence surface treats specially and none of the fourteen it treats as an object reference.
 */
export const reviewObservationDetail: ReviewObservationDetail = {
	id: shortfall.id,
	agentJobId: shortfall.agentJobId,
	artifact: reviewArtifact,
	area: shortfall.area,
	assessment: "BAD",
	claimCurrentness: "CURRENT",
	confidence: shortfall.confidence,
	evidence: {
		citations: [
			{
				sourceKind: "scm.pull-request.diff",
				artifactPath: "inputs/context/diff.patch",
				path: "webapp/src/routes/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings/index.tsx",
				side: "NEW",
				startLine: 7,
				endLine: 8,
				quote:
					'export const Route = createFileRoute(\n\t"/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings/",\n);',
				quoteRedacted: false,
			},
			{
				sourceKind: "scm.pull-request.diff",
				artifactPath: "inputs/context/diff.patch",
				path: "webapp/src/components/admin/practice-reviews/PracticeReviewsLayout.tsx",
				side: "NEW",
				startLine: 30,
				endLine: 30,
				quote: '\t\tlabel: "Observations",',
				quoteRedacted: false,
			},
			{
				sourceKind: "scm.pull-request.comments",
				artifactPath: "inputs/context/comments.json",
				path: "Comment by @grace",
				startLine: 14,
				endLine: 14,
				quote: "The nav says Observations but the URL still says findings — is that deliberate?",
				quoteRedacted: false,
			},
			{
				sourceKind: "outline.documents",
				artifactPath: "inputs/context/outline/naming.md",
				path: "Naming guideline",
				startLine: 22,
				endLine: 22,
				quote:
					"A URL is product copy. It is read, shared and bookmarked more often than a heading.",
				quoteRedacted: false,
			},
		],
	},
	feedback: [
		{
			feedbackId: withheldFeedback.id,
			agentJobId: withheldFeedback.agentJobId,
			channel: "IN_CONTEXT",
			createdAt: withheldFeedback.createdAt,
			deliveryState: "SUPPRESSED",
			role: "PRIMARY",
			suppressionReason: "ARTIFACT_MERGED",
		},
	],
	observedAt: shortfall.observedAt,
	practiceName: shortfall.practiceName,
	practiceSlug: shortfall.practiceSlug,
	presence: "PRESENT",
	reasoning:
		"The URL segment names the step of the pipeline that produced the record rather than the record itself, so an operator following a link has to already know how the system is built to know what they are about to read. The navigation above it, the page heading and the wire contract all say observation.",
	severity: "MAJOR",
	subject: shortfall.subject,
	title: shortfall.title,
};
