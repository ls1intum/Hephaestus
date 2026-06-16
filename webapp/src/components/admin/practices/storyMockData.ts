import type { Practice } from "@/api/types.gen";

export const mockPractices: Practice[] = [
	{
		id: 1,
		slug: "pr-description-quality",
		name: "PR Description Quality",
		triggerEvents: ["PullRequestCreated", "PullRequestReady"],
		criteria:
			"## PR Description Quality\n\nEvaluate whether the pull request description provides sufficient context, motivation, and testing steps.\n\n### Required Elements\n- Summary of changes\n- Motivation / why\n- Testing steps\n- Link to issue",
		artifactType: "PULL_REQUEST",
		polarity: "DESIRABLE",
		active: true,
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-15"),
	},
	{
		id: 2,
		slug: "code-review-thoroughness",
		name: "Code Review Thoroughness",
		triggerEvents: ["ReviewSubmitted"],
		criteria:
			"## Code Review Thoroughness\n\nEvaluate depth and quality of code reviews. Reviewers should engage with logic and design, not just style.",
		artifactType: "PULL_REQUEST",
		polarity: "DESIRABLE",
		active: true,
		createdAt: new Date("2025-06-02"),
		updatedAt: new Date("2025-06-14"),
	},
	{
		id: 3,
		slug: "test-coverage",
		name: "Test Coverage",
		triggerEvents: ["PullRequestCreated", "PullRequestSynchronized"],
		criteria:
			"## Test Coverage\n\nChecks that new code includes appropriate test coverage. Critical paths and edge cases should be tested.",
		artifactType: "PULL_REQUEST",
		polarity: "DESIRABLE",
		active: false,
		createdAt: new Date("2025-06-03"),
		updatedAt: new Date("2025-06-10"),
	},
];

/** A minimal active practice with no precompute script. */
export const mockPracticeNoCategory: Practice = {
	id: 5,
	slug: "error-state-handling",
	name: "Error State Handling",
	triggerEvents: ["PullRequestCreated"],
	criteria:
		"## Error State Handling\n\nEvaluates whether the code properly handles and surfaces errors to the user instead of silently swallowing them.",
	artifactType: "PULL_REQUEST",
	polarity: "DESIRABLE",
	active: true,
	createdAt: new Date("2025-06-05"),
	updatedAt: new Date("2025-06-17"),
};

/** Practice with an extremely long name and verbose criteria — tests text overflow. */
export const mockPracticeLongText: Practice = {
	id: 6,
	slug: "very-long-practice-name-to-test-overflow-in-card-layouts",
	name: "Extremely Verbose Practice Name That Tests Text Wrapping and Overflow Behavior in Card Layouts",
	triggerEvents: [
		"PullRequestCreated",
		"PullRequestReady",
		"PullRequestSynchronized",
		"ReviewSubmitted",
	],
	criteria:
		"## Very Long Criteria\n\nThis is a multi-paragraph criteria block designed to test the line-clamp behavior on the card preview.\n\n### Section 1\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n\n### Section 2\nUt enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.\n\n### Section 3\nDuis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
	artifactType: "PULL_REQUEST",
	polarity: "DESIRABLE",
	active: true,
	createdAt: new Date("2025-06-06"),
	updatedAt: new Date("2025-06-18"),
};

export const mockPracticeWithAllTriggers: Practice = {
	id: 4,
	slug: "commit-discipline",
	name: "Commit Discipline",
	triggerEvents: [
		"PullRequestCreated",
		"PullRequestReady",
		"PullRequestSynchronized",
		"ReviewSubmitted",
	],
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
	artifactType: "PULL_REQUEST",
	polarity: "DESIRABLE",
	active: true,
	createdAt: new Date("2025-06-04"),
	updatedAt: new Date("2025-06-16"),
};

export const mockGoals: import("@/api/types.gen").PracticeArea[] = [
	{
		id: 1,
		slug: "review-ready-work",
		name: "Submitting review-ready work",
		description: "Make each change easy and fast to review.",
		active: true,
		displayOrder: 1,
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-01"),
	},
	{
		id: 2,
		slug: "actionable-issue-authoring",
		name: "Writing issues a maintainer can act on",
		description: "Give a maintainer enough to start work.",
		active: true,
		displayOrder: 3,
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-01"),
	},
];
