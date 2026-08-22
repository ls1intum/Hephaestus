import type {
	AreaAutonomyRollup,
	AutonomyAssignment,
	AutonomyRollup,
	Practice,
	PracticeReviewSettings,
} from "@/api/types.gen";
import { mockReviewSettings } from "@/components/admin/practices/story-mock-data";
import type { PracticeAutonomy } from "@/lib/practice-autonomy";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";

/**
 * Fixtures that resolve the inheritance chain the way the server does, so a fixture is either a state
 * the server can reach or a compile error. Hand-written rows drift: a practice at Review before sending under an
 * area at Off, counted by the rollup as Send automatically, looks plausible and cannot happen.
 */

export interface PracticeSpec {
	name: string;
	/** The autonomy held on the practice itself; absent means it inherits. */
	override?: PracticeAutonomy;
	/** False for a practice that cannot be reviewed automatically — the server pins those to Off. */
	reviewable?: boolean;
	/** Optional on the API: a locally written practice carries none, and the row has to read without it. */
	whyItMatters?: string;
	/** Defaults to a pull request. */
	artifactKind?: string;
}

export interface AreaSpec {
	/** Null is the group of practices that belong to no area; it holds no autonomy of its own. */
	slug: string | null;
	name: string | null;
	override?: PracticeAutonomy;
	practices: PracticeSpec[];
}

export interface AutonomyFixture {
	settings: PracticeReviewSettings;
	rollup: AutonomyRollup;
	practices: Practice[];
}

const emptyCounts = (): Record<PracticeAutonomy, number> => ({
	OFF: 0,
	HUMAN_APPROVAL: 0,
	AUTOMATIC: 0,
});

function assignment(
	override: PracticeAutonomy | undefined,
	effective: PracticeAutonomy,
	source: AutonomyAssignment["source"],
): AutonomyAssignment {
	// `inherited` follows the override, never the source: an area that chose its own autonomy reports
	// source AREA and inherited false.
	return { effective, override, source, inherited: override == null };
}

export function buildAutonomyFixture({
	workspaceDefault,
	areas,
}: {
	/** Absent means this workspace has never chosen, so Review before sending applies. */
	workspaceDefault?: PracticeAutonomy;
	areas: AreaSpec[];
}): AutonomyFixture {
	const effectiveDefault: PracticeAutonomy = workspaceDefault ?? "HUMAN_APPROVAL";
	const practices: Practice[] = [];
	const rollupAreas: AreaAutonomyRollup[] = [];
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
				artifactKind: spec.artifactKind ?? "scm.pull_request",
				whyItMatters: spec.whyItMatters,
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
				autonomy: assignment(held, effective, source),
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
			autonomy:
				area.slug === null
					? assignment(workspaceDefault, effectiveDefault, "WORKSPACE")
					: assignment(area.override, areaEffective, area.override ? "AREA" : "WORKSPACE"),
		});
	}

	return {
		settings: mockReviewSettings({
			defaultAutonomy: effectiveDefault,
			defaultAutonomyOverride: workspaceDefault,
		}),
		rollup: {
			counts: workspaceCounts,
			areas: rollupAreas,
			workspaceDefault: assignment(workspaceDefault, effectiveDefault, "WORKSPACE"),
		},
		practices,
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
 * Deliberately lopsided: most of it inherits, a handful of areas and practices were changed by hand,
 * and one practice cannot be reviewed at all. A fixture where everything is set says nothing about
 * whether the inherited case recedes.
 */
export function scaleFixture(): AutonomyFixture {
	return buildAutonomyFixture({
		workspaceDefault: "HUMAN_APPROVAL",
		areas: SCALE_AREA_NAMES.map((name, index) => ({
			slug: slugify(name),
			name,
			override: index === 2 ? "OFF" : index === 7 ? "AUTOMATIC" : undefined,
			practices: SCALE_PRACTICE_NAMES.map((suffix, practiceIndex) => ({
				name: `${name}: ${suffix}`,
				override:
					index === 0 && practiceIndex === 0
						? "AUTOMATIC"
						: index === 4 && practiceIndex === 1
							? "OFF"
							: undefined,
				reviewable: !(index === 9 && practiceIndex === 3),
			})),
		})),
	});
}
