import type {
	ReviewArtifact,
	ReviewFeedback,
	ReviewFeedbackDetail,
	ReviewFinding,
	ReviewFindingDetail,
} from "@/api/types.gen";

export const reviewArtifact: ReviewArtifact = {
	id: 42,
	type: "scm.pull_request",
	provider: "GITHUB",
	number: 1423,
	repositoryName: "ls1intum/Hephaestus",
	title: "Admin read surface for detection output and withheld feedback",
	url: "https://github.com/ls1intum/Hephaestus/pull/1423",
};

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
		findingCount: 2,
		recipient: { id: 7, login: "ada", name: "Ada Lovelace" },
		subject: { id: 7, login: "ada", name: "Ada Lovelace" },
	},
	{
		id: "44444444-4444-4444-4444-444444444444",
		agentJobId: "22222222-2222-2222-2222-222222222222",
		artifact: reviewArtifact,
		bodyPreview:
			"Consider naming the boundary after the product concept rather than the storage model.",
		bodyTruncated: false,
		channel: "IN_CONTEXT",
		createdAt: new Date("2026-07-28T12:10:00Z"),
		deliveryState: "SUPPRESSED",
		findingCount: 1,
		recipient: { id: 9, login: "grace", name: "Grace Hopper" },
		subject: { id: 9, login: "grace", name: "Grace Hopper" },
		suppressionReason: "ARTIFACT_MERGED",
	},
] satisfies [ReviewFeedback, ReviewFeedback];

export const reviewFindings = [
	{
		id: "55555555-5555-5555-5555-555555555555",
		agentJobId: "11111111-1111-1111-1111-111111111111",
		artifact: reviewArtifact,
		area: { slug: "code-quality", name: "Code quality" },
		assessment: "GOOD",
		claimCurrentness: "CURRENT",
		confidence: 0.94,
		feedbackDisposition: { delivered: 1, failed: 0, prepared: 1, superseded: 0, suppressed: 0 },
		observedAt: new Date("2026-07-28T13:40:00Z"),
		origin: "LIVE",
		practiceName: "Thin controllers",
		practiceSlug: "thin-controllers",
		presence: "PRESENT",
		subject: { id: 7, login: "ada", name: "Ada Lovelace" },
		title: "The controller delegates review queries",
	},
	{
		id: "66666666-6666-6666-6666-666666666666",
		agentJobId: "22222222-2222-2222-2222-222222222222",
		artifact: reviewArtifact,
		area: { slug: "architecture", name: "Architecture" },
		assessment: "BAD",
		claimCurrentness: "CURRENT",
		confidence: 0.87,
		feedbackDisposition: { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 1 },
		observedAt: new Date("2026-07-28T12:08:00Z"),
		// A campaign's finding, so the surface can be seen distinguishing it from a live one.
		origin: "BACKFILL",
		practiceName: "Product language",
		practiceSlug: "product-language",
		presence: "PRESENT",
		severity: "MAJOR",
		subject: { id: 9, login: "grace", name: "Grace Hopper" },
		title: "The route exposes an internal detection term",
	},
] satisfies [ReviewFinding, ReviewFinding];

const suppressedFeedback = reviewFeedback[1];
const improvementFinding = reviewFindings[1];

export const reviewFeedbackDetail: ReviewFeedbackDetail = {
	id: suppressedFeedback.id,
	agentJobId: suppressedFeedback.agentJobId,
	artifact: reviewArtifact,
	body: "## What could improve\n\nName the route after the operator's task, not an internal pipeline stage.",
	channel: "IN_CONTEXT",
	createdAt: suppressedFeedback.createdAt,
	deliveryState: "SUPPRESSED",
	findings: [
		{
			findingId: improvementFinding.id,
			area: improvementFinding.area,
			assessment: "BAD",
			claimCurrentness: "CURRENT",
			confidence: improvementFinding.confidence,
			observedAt: improvementFinding.observedAt,
			ordinal: 0,
			practiceName: improvementFinding.practiceName,
			practiceSlug: improvementFinding.practiceSlug,
			presence: "PRESENT",
			role: "PRIMARY",
			severity: "MAJOR",
			title: improvementFinding.title,
		},
	],
	placements: [],
	recipient: suppressedFeedback.recipient,
	subject: suppressedFeedback.subject,
	suppressionReason: "ARTIFACT_MERGED",
};

export const reviewFindingDetail: ReviewFindingDetail = {
	id: improvementFinding.id,
	agentJobId: improvementFinding.agentJobId,
	artifact: reviewArtifact,
	area: improvementFinding.area,
	assessment: "BAD",
	claimCurrentness: "CURRENT",
	confidence: improvementFinding.confidence,
	evidence: {
		citations: [
			{
				sourceKind: "scm.pull-request.diff",
				artifactPath: "inputs/context/diff.patch",
				path: "webapp/src/routes/_authenticated/w/$workspaceSlug/admin/practices/reviews/targets/$artifactKind/$artifactId.tsx",
				side: "NEW",
				startLine: 1,
				endLine: 1,
				quote:
					"const routeName = 'detection-output-that-is-much-longer-than-the-available-mobile-viewport';",
				quoteRedacted: false,
			},
		],
	},
	feedback: [
		{
			feedbackId: suppressedFeedback.id,
			agentJobId: suppressedFeedback.agentJobId,
			channel: "IN_CONTEXT",
			createdAt: suppressedFeedback.createdAt,
			deliveryState: "SUPPRESSED",
			role: "PRIMARY",
			suppressionReason: "ARTIFACT_MERGED",
		},
	],
	observedAt: improvementFinding.observedAt,
	practiceName: improvementFinding.practiceName,
	practiceSlug: improvementFinding.practiceSlug,
	presence: "PRESENT",
	reasoning:
		"The route exposes an implementation term that does not match the operator's investigation task.",
	severity: "MAJOR",
	subject: improvementFinding.subject,
	title: improvementFinding.title,
};
