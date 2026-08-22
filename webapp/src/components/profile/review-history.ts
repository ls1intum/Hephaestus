import type { ObservationDetail, ObservationList, PracticeAreaReviewMoment } from "@/api/types.gen";
import type { ProviderType } from "@/lib/provider";

/**
 * The kinds this surface renders specially, without closing the set: a workspace can review kinds this
 * build has never heard of, and the API carries the kind as an open string. Listing the known ones keeps
 * completion and typo-checking on the comparisons below; the `string` arm keeps an unknown kind
 * renderable instead of turning it into a type error.
 */
export type ArtifactKind = "PULL_REQUEST" | "ISSUE" | "CONVERSATION_THREAD" | (string & {});

export interface ReviewFinding {
	observationId: string;
	feedbackId?: string;
	/** The recipient's usefulness rating for the delivered feedback; unrelated to standing. */
	helpful?: boolean;
	practiceSlug: string;
	practiceName: string;
	/** Concrete reviewer finding; older Storybook fixtures fall back to the practice name. */
	title?: string;
	/**
	 * `INCONCLUSIVE` is a verdict, not a gap: the reviewer looked and was not certain enough to claim
	 * either way. It must stay distinguishable from a practice that was never observed.
	 */
	presence: "PRESENT" | "ABSENT" | "NOT_APPLICABLE" | "INCONCLUSIVE";
	assessment?: "GOOD" | "BAD";
	severity?: "CRITICAL" | "MAJOR" | "MINOR" | "INFO";
	reasoning?: string;
	guidance?: string;
	evidence?: string;
	/** Stable identity for comparing the same concern across runs of one artifact. */
	recurrenceKey?: string;
}

export interface ReviewRun {
	reviewId: string;
	reviewedAt: string;
	findings: ReviewFinding[];
}

export interface ReviewedArtifact {
	artifactType: ArtifactKind;
	artifactId: number;
	provider: "GITHUB" | "GITLAB" | "SLACK";
	number?: number;
	title?: string;
	repositoryName?: string;
	channelName?: string;
	url?: string;
	messageCount?: number;
	runs: ReviewRun[];
}

export interface ObservationDetailState {
	isLoading: boolean;
	detail?: ObservationDetail;
	error?: unknown;
}

export interface FindingChange {
	direction: "IMPROVED" | "REGRESSED";
	previousAt: string;
}

export interface ReviewHistoryEntry {
	artifact: ReviewedArtifact;
	run: ReviewRun;
	findings: ReviewFinding[];
	earlierRuns: ReviewRun[];
}

/**
 * Adapts the real observation feed into review moments. One delivery stores the same observedAt on
 * every finding, so artifact type + artifact id + timestamp identify a run without exposing the
 * internal agent-job id. Artifact display metadata remains optional until the API provides it.
 */
export function observationsToReviewedArtifacts(
	observations: ObservationList[],
	providerType: ProviderType,
): ReviewedArtifact[] {
	const artifacts = new Map<string, ReviewedArtifact>();

	for (const observation of observations) {
		const artifactKey = `${observation.artifactKind}:${observation.artifactId}`;
		let artifact = artifacts.get(artifactKey);
		if (!artifact) {
			artifact = {
				artifactType: observation.artifactKind,
				artifactId: observation.artifactId,
				provider:
					observation.artifactKind === "CONVERSATION_THREAD"
						? "SLACK"
						: providerType === "GITLAB"
							? "GITLAB"
							: "GITHUB",
				runs: [],
			};
			artifacts.set(artifactKey, artifact);
		}

		const reviewedAt = new Date(observation.observedAt).toISOString();
		let run = artifact.runs.find((candidate) => candidate.reviewedAt === reviewedAt);
		if (!run) {
			run = {
				reviewId: `${artifactKey}:${reviewedAt}`,
				reviewedAt,
				findings: [],
			};
			artifact.runs.push(run);
		}

		run.findings.push({
			observationId: observation.id,
			practiceSlug: observation.practiceSlug,
			practiceName: observation.practiceName,
			title: observation.summary,
			presence: observation.presence,
			assessment: observation.assessment,
			severity: observation.severity,
		});
	}

	return [...artifacts.values()];
}

/** Adapts the run-based learner endpoint without reconstructing review boundaries in the browser. */
export function reviewMomentsToReviewedArtifacts(
	moments: PracticeAreaReviewMoment[],
): ReviewedArtifact[] {
	const artifacts = new Map<string, ReviewedArtifact>();

	for (const moment of moments) {
		const key = `${moment.artifact.type}:${moment.artifact.id}`;
		let artifact = artifacts.get(key);
		if (!artifact) {
			artifact = {
				artifactType: moment.artifact.type,
				artifactId: moment.artifact.id,
				provider:
					moment.artifact.type === "CONVERSATION_THREAD"
						? "SLACK"
						: moment.artifact.provider === "GITLAB"
							? "GITLAB"
							: "GITHUB",
				number: moment.artifact.number,
				title: moment.artifact.title,
				repositoryName: moment.artifact.repositoryName,
				channelName: moment.artifact.channelName,
				url: moment.artifact.url,
				runs: [],
			};
			artifacts.set(key, artifact);
		}

		artifact.runs.push({
			reviewId: moment.reviewId,
			reviewedAt: new Date(moment.reviewedAt).toISOString(),
			findings: moment.findings.map((finding) => ({
				observationId: finding.observationId,
				feedbackId: finding.feedbackId,
				// The API records HELPFUL / UNHELPFUL / INCORRECT; this surface offers a thumb up or down,
				// so anything that is not HELPFUL reads as "not helpful" here. INCORRECT and the optional
				// comment have no control yet and are deliberately not invented in the mapping.
				helpful: finding.rating == null ? undefined : finding.rating === "HELPFUL",
				practiceSlug: finding.practiceSlug,
				practiceName: finding.practiceName,
				title: finding.title,
				presence: finding.presence,
				assessment: finding.assessment,
				severity: finding.severity,
				recurrenceKey: finding.recurrenceKey,
			})),
		});
	}

	return [...artifacts.values()];
}
