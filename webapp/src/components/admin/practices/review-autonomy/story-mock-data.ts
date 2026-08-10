import type {
	AreaReviewTierRollup,
	Practice,
	PracticeReviewSettings,
	ReviewTierAssignment,
	ReviewTierRollup,
} from "@/api/types.gen";
import { REVIEW_TIER_ORDER, type ReviewTier } from "@/lib/review-tiers";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";

/**
 * Fixtures that resolve the inheritance chain the way the server does.
 *
 * <p>Hand-written rows drift: a story showing a practice at Observe under an area at Off, with a
 * rollup counting it as Deliver, would look plausible and would be testing a state the API cannot
 * produce. Everything here is derived from the overrides a story declares, so a fixture is either a
 * state the server can reach or a compile error.
 */

export interface PracticeSpec {
	name: string;
	/** The tier held on the practice itself; absent means it inherits. */
	override?: ReviewTier;
	/** False for a practice Hephaestus cannot review — the server pins those to Off. */
	reviewable?: boolean;
}

export interface AreaSpec {
	/** Null is the group of practices that belong to no area; it holds no tier of its own. */
	slug: string | null;
	name: string | null;
	override?: ReviewTier;
	practices: PracticeSpec[];
}

export interface AutonomyFixture {
	settings: PracticeReviewSettings;
	rollup: ReviewTierRollup;
	practices: Practice[];
}

const emptyCounts = (): Record<string, number> =>
	Object.fromEntries(REVIEW_TIER_ORDER.map((tier) => [tier, 0]));

function assignment(
	override: ReviewTier | undefined,
	effective: ReviewTier,
	source: ReviewTierAssignment["source"],
): ReviewTierAssignment {
	// `inherited` follows the override, never the source: an area that chose its own tier reports
	// source AREA and inherited false, and conflating the two is the bug this fixture must not hide.
	return { effective, override, source, inherited: override == null };
}

export function buildAutonomyFixture({
	workspaceDefault,
	feedbackReach,
	areas,
}: {
	/** Absent means this workspace has never chosen, so Deliver applies. */
	workspaceDefault?: ReviewTier;
	feedbackReach?: PracticeReviewSettings["feedbackReach"];
	areas: AreaSpec[];
}): AutonomyFixture {
	const effectiveDefault: ReviewTier = workspaceDefault ?? "DELIVER";
	const practices: Practice[] = [];
	const rollupAreas: AreaReviewTierRollup[] = [];
	const workspaceCounts = emptyCounts();
	let id = 1;

	for (const area of areas) {
		const areaEffective = area.override ?? effectiveDefault;
		const counts = emptyCounts();
		let overriddenCount = 0;

		for (const spec of area.practices) {
			// The server writes Off onto a practice it cannot review, rather than letting it inherit.
			const held = spec.reviewable === false ? "OFF" : spec.override;
			const effective = held ?? areaEffective;
			const source = held ? "PRACTICE" : area.override ? "AREA" : "WORKSPACE";
			if (held) overriddenCount += 1;
			counts[effective] += 1;
			workspaceCounts[effective] += 1;
			practices.push({
				id: id++,
				slug: `${area.slug ?? "unassigned"}-${slugify(spec.name)}`,
				name: spec.name,
				areaSlug: area.slug ?? undefined,
				bindings: [mockPullRequestBinding],
				criteria: `## ${spec.name}\n\nWhat a review looks for.`,
				artifactKind: "scm.pull_request",
				automatedReviewPolicy:
					spec.reviewable === false
						? {
								...mockPullRequestPolicy,
								automatedReview: {
									...mockPullRequestPolicy.automatedReview,
									mode: "NONE",
									evidenceSufficiency: "NONE",
								},
							}
						: mockPullRequestPolicy,
				automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
				displayOrder: practices.length,
				reviewTier: assignment(held, effective, source),
				createdAt: new Date("2026-01-01"),
				updatedAt: new Date("2026-01-02"),
			});
		}

		rollupAreas.push({
			areaSlug: area.slug ?? undefined,
			areaName: area.name ?? undefined,
			counts,
			overriddenCount,
			// The no-area bucket carries the workspace's answer; it is not a row that can hold one.
			reviewTier:
				area.slug === null
					? assignment(workspaceDefault, effectiveDefault, "WORKSPACE")
					: assignment(area.override, areaEffective, area.override ? "AREA" : "WORKSPACE"),
		});
	}

	return {
		settings: mockReviewSettings({
			defaultReviewTier: effectiveDefault,
			defaultReviewTierOverride: workspaceDefault,
			feedbackReach: feedbackReach ?? "ON_THE_WORK",
			feedbackReachOverride: feedbackReach,
		}),
		rollup: {
			counts: workspaceCounts,
			areas: rollupAreas,
			feedbackReach: feedbackReach ?? "ON_THE_WORK",
			workspaceDefault: assignment(workspaceDefault, effectiveDefault, "WORKSPACE"),
		},
		practices,
	};
}

export function mockReviewSettings(
	overrides: Partial<PracticeReviewSettings> = {},
): PracticeReviewSettings {
	return {
		cooldownMinutes: 30,
		defaultReviewTier: "DELIVER",
		deliverToMerged: true,
		feedbackReach: "ON_THE_WORK",
		reviewScope: { targetBranches: [], repositories: [] },
		runForAllUsers: true,
		...overrides,
	};
}

const slugify = (name: string) =>
	name
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, "-")
		.replace(/^-|-$/g, "");

const SCALE_AREA_NAMES = [
	"Pull request hygiene",
	"Testing",
	"Documentation",
	"Error handling",
	"Security",
	"Performance",
	"Accessibility",
	"API design",
	"Data modelling",
	"Observability",
	"Dependency care",
	"Release readiness",
	"Code review conduct",
	"Issue hygiene",
	"Incident response",
	"Configuration",
	"Migrations",
	"Front-end structure",
	"Back-end structure",
	"Concurrency",
	"Caching",
	"Build and CI",
	"Naming",
	"Refactoring",
	"Onboarding docs",
];

const SCALE_PRACTICE_NAMES = [
	"states the motivation",
	"links the issue it closes",
	"lists the steps a reviewer ran",
	"keeps the change reviewable in one sitting",
];

/**
 * Twenty-five areas, four practices each — the size at which editing rows one at a time stops being
 * possible and the screen has to earn its keep.
 *
 * <p>Deliberately lopsided: most of it inherits, a handful of areas and practices were changed by
 * hand, and one practice cannot be reviewed at all. A fixture where everything is set says nothing
 * about whether the inherited case recedes.
 */
export function scaleFixture(): AutonomyFixture {
	return buildAutonomyFixture({
		workspaceDefault: "OBSERVE",
		areas: SCALE_AREA_NAMES.map((name, index) => ({
			slug: slugify(name),
			name,
			override: index === 2 ? "OFF" : index === 7 ? "DELIVER" : undefined,
			practices: SCALE_PRACTICE_NAMES.map((suffix, practiceIndex) => ({
				name: `${name}: ${suffix}`,
				override:
					index === 0 && practiceIndex === 0
						? "DELIVER"
						: index === 4 && practiceIndex === 1
							? "OFF"
							: undefined,
				reviewable: !(index === 9 && practiceIndex === 3),
			})),
		})),
	});
}
