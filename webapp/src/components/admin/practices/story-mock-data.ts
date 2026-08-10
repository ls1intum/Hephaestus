import type { Practice, PracticeArea, ReviewTierAssignment } from "@/api/types.gen";
import type { ReviewTier } from "@/lib/review-tiers";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockMergeBinding,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";

/**
 * The ordinary state of a practice nobody has configured: it holds no tier of its own and follows the
 * workspace default. Most fixtures want this, because a catalog full of overrides is not what an admin
 * opens the screen to.
 */
export function inheritedTier(effective: ReviewTier = "DELIVER"): ReviewTierAssignment {
	return { effective, source: "WORKSPACE", inherited: true };
}

/** A tier somebody chose on this practice itself — the case a story is showing on purpose. */
export function chosenTier(effective: ReviewTier): ReviewTierAssignment {
	return { effective, override: effective, source: "PRACTICE", inherited: false };
}

export const mockPractices: Practice[] = [
	{
		id: 1,
		slug: "pr-description-quality",
		name: "PR Description Quality",
		bindings: [mockPullRequestBinding],
		criteria:
			"## PR Description Quality\n\nEvaluate whether the pull request description provides sufficient context, motivation, and testing steps.\n\n### Required Elements\n- Summary of changes\n- Motivation / why\n- Testing steps\n- Link to issue",
		whyItMatters:
			"A clear description lets reviewers understand intent without reverse-engineering the diff, speeding up review and reducing back-and-forth.",
		whatGoodLooksLike:
			"A PR opens with a one-paragraph summary, links the issue, and lists the exact steps a reviewer ran to verify it.",
		artifactKind: "scm.pull_request",
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		displayOrder: 0,
		reviewTier: inheritedTier(),
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-15"),
	},
	{
		id: 2,
		slug: "code-review-thoroughness",
		name: "Code Review Thoroughness",
		bindings: [
			{
				signals: ["scm.pull_request.reviewed"],
				needs: mockPullRequestBinding.needs,
			},
		],
		criteria:
			"## Code Review Thoroughness\n\nEvaluate depth and quality of code reviews. Reviewers should engage with logic and design, not just style.",
		artifactKind: "scm.pull_request",
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		displayOrder: 0,
		reviewTier: inheritedTier(),
		createdAt: new Date("2025-06-02"),
		updatedAt: new Date("2025-06-14"),
	},
	{
		id: 3,
		slug: "test-coverage",
		name: "Test Coverage",
		bindings: [
			{
				signals: ["scm.pull_request.opened", "scm.pull_request.synchronized"],
				needs: mockPullRequestBinding.needs,
			},
		],
		criteria:
			"## Test Coverage\n\nChecks that new code includes appropriate test coverage. Critical paths and edge cases should be tested.",
		artifactKind: "scm.pull_request",
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		displayOrder: 0,
		reviewTier: chosenTier("OFF"),
		createdAt: new Date("2025-06-03"),
		updatedAt: new Date("2025-06-10"),
	},
];

export const mockUnassignedPractice: Practice = {
	id: 5,
	slug: "error-state-handling",
	name: "Error State Handling",
	bindings: [
		{ signals: ["scm.pull_request.opened"], needs: mockPullRequestBinding.needs, onDrafts: true },
	],
	criteria:
		"## Error State Handling\n\nEvaluates whether the code properly handles and surfaces errors to the user instead of silently swallowing them.",
	artifactKind: "scm.pull_request",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	displayOrder: 0,
	reviewTier: inheritedTier(),
	createdAt: new Date("2025-06-05"),
	updatedAt: new Date("2025-06-17"),
};

export const mockPracticeLongText: Practice = {
	id: 6,
	slug: "very-long-practice-name-to-test-overflow-in-card-layouts",
	name: "Extremely Verbose Practice Name That Tests Text Wrapping and Overflow Behavior in Card Layouts",
	bindings: [mockPullRequestBinding, mockMergeBinding],
	criteria:
		"## Very Long Criteria\n\nThis is a multi-paragraph criteria block designed to test the line-clamp behavior on the card preview.\n\n### Section 1\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n\n### Section 2\nUt enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.\n\n### Section 3\nDuis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
	artifactKind: "scm.pull_request",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	displayOrder: 0,
	reviewTier: inheritedTier(),
	createdAt: new Date("2025-06-06"),
	updatedAt: new Date("2025-06-18"),
};

export const mockPracticeWithAllTriggers: Practice = {
	id: 4,
	slug: "commit-discipline",
	name: "Commit Discipline",
	bindings: [mockPullRequestBinding, mockMergeBinding],
	criteria:
		"## Commit Discipline\n\nEach commit message must:\n- Start with a type prefix (feat, fix, refactor, etc.)\n- Have a descriptive subject (not just issue numbers)\n- Reference the related issue\n\n### Anti-patterns to Flag\n- `fixes #123` with no description\n- Branch-slug-format titles like `feature/ABC-123`\n- Single-word messages like `update` or `fix`",
	precomputeScript: [
		'import { readDiff } from "../lib/diff";',
		'import { parseDiffFiles } from "../lib/parse";',
		"",
		"const diff = await readDiff();",
		"const files = parseDiffFiles(diff);",
		"const findings: string[] = [];",
		"",
		"for (const file of files) {",
		'  if (file.path.includes("commit")) {',
		'    findings.push("Changed: " + file.path);',
		"  }",
		"}",
		"",
		"export default { findings };",
	].join("\n"),
	artifactKind: "scm.pull_request",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	displayOrder: 0,
	reviewTier: inheritedTier(),
	createdAt: new Date("2025-06-04"),
	updatedAt: new Date("2025-06-16"),
};

export const mockAreas: PracticeArea[] = [
	{
		id: 1,
		slug: "review-ready-work",
		name: "Submitting review-ready work",
		description: "Make each change easy and fast to review.",
		visibleInPracticeDashboards: true,
		displayOrder: 1,
		reviewTier: inheritedTier(),
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-01"),
	},
	{
		id: 2,
		slug: "actionable-issue-authoring",
		name: "Writing issues a maintainer can act on",
		description: "Give a maintainer enough to start work.",
		visibleInPracticeDashboards: true,
		displayOrder: 3,
		reviewTier: inheritedTier(),
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-01"),
	},
];
