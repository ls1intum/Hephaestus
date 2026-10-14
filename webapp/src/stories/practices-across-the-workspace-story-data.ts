import type {
	GroupRef,
	OutcomeVector,
	PracticeWorkspaceOverview,
	PracticeWorkspaceTile,
} from "@/api/types.gen";
import type { WorkspaceGroup } from "@/components/practices-across-the-workspace/workspace-groups";

const ACTING: GroupRef = {
	slug: "acting-on-review-feedback",
	name: "Acting on review feedback",
	icon: "MessageSquareReply",
	color: "cyan",
};
const COMMUNICATION: GroupRef = {
	slug: "communication",
	name: "Communicating in the open",
	icon: "MessageCircle",
	color: "violet",
};
const PACKAGING: GroupRef = {
	slug: "review-ready-work",
	name: "Packaging work for review",
	icon: "Package",
	color: "sky",
};
const TESTING: GroupRef = {
	slug: "testing-discipline",
	name: "Testing your changes",
	icon: "TestTube",
	color: "red",
};

export const outcomes = ([met, notMet, doesNotApply, couldNotDetermine]: [
	number,
	number,
	number,
	number,
]): OutcomeVector => ({ met, notMet, doesNotApply, couldNotDetermine });

export const NO_OUTCOMES: OutcomeVector = outcomes([0, 0, 0, 0]);

const tile = (
	group: GroupRef | null,
	practiceSlug: string,
	practiceName: string,
	developers: number,
	counts: OutcomeVector,
): PracticeWorkspaceTile => ({ practiceSlug, practiceName, group, developers, outcomes: counts });

const PACKAGING_TILES: PracticeWorkspaceTile[] = [
	tile(
		PACKAGING,
		"keep-the-diff-reviewable",
		"Keep the diff reviewable in one sitting",
		21,
		outcomes([14, 9, 2, 1]),
	),
	tile(
		PACKAGING,
		"explain-the-change",
		"Explain what the change does and why",
		21,
		outcomes([12, 11, 0, 2]),
	),
	tile(
		PACKAGING,
		"scope-to-one-concern",
		"Scope the change to one concern",
		3,
		outcomes([2, 1, 0, 0]),
	),
	tile(
		PACKAGING,
		"mark-ready-and-link",
		"Mark the change ready and link its issue",
		0,
		NO_OUTCOMES,
	),
	tile(PACKAGING, "keep-history-clean", "Keep the history readable", 18, outcomes([15, 4, 3, 0])),
];

export const ACROSS_WORKSPACE: PracticeWorkspaceOverview = {
	developers: 28,
	practices: [
		tile(
			ACTING,
			"answer-every-comment",
			"Answer every review comment",
			20,
			outcomes([16, 6, 1, 0]),
		),
		tile(
			ACTING,
			"re-request-review",
			"Ask for review again after changes",
			17,
			outcomes([9, 10, 4, 1]),
		),
		tile(
			COMMUNICATION,
			"write-in-the-open",
			"Discuss decisions where the team can see them",
			12,
			outcomes([8, 5, 9, 3]),
		),
		...PACKAGING_TILES,
		tile(TESTING, "test-the-change", "Add tests for the change", 24, outcomes([10, 17, 2, 4])),
		tile(null, "sign-commits", "Sign your commits", 9, outcomes([9, 0, 0, 0])),
	],
};

export const PACKAGING_GROUP: WorkspaceGroup = { ...PACKAGING, practices: PACKAGING_TILES };

export const MANY_PRACTICES: PracticeWorkspaceTile[] = Array.from({ length: 24 }, (_, index) =>
	tile(PACKAGING, `practice-${index + 1}`, `Practice ${index + 1}`, 12, outcomes([8, 5, 1, 0])),
);

export const NO_RESULTS_WORKSPACE: PracticeWorkspaceOverview = {
	...ACROSS_WORKSPACE,
	practices: ACROSS_WORKSPACE.practices.map((each) => ({
		...each,
		developers: 0,
		outcomes: NO_OUTCOMES,
	})),
};

export const EMPTY_WORKSPACE: PracticeWorkspaceOverview = { developers: 28, practices: [] };

/** Many groups, every one listed. */
export const MANY_GROUPS_WORKSPACE: PracticeWorkspaceOverview = {
	developers: 28,
	practices: Array.from({ length: 26 }, (_, index) =>
		tile(
			{ ...ACTING, slug: `group-${index + 1}`, name: `Practice group ${index + 1}` },
			`practice-${index + 1}`,
			`Practice ${index + 1}`,
			12,
			outcomes([8, 5, 1, 0]),
		),
	),
};
