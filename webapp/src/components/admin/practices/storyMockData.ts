import type { Practice } from "@/api/types.gen";

export const mockPractices: Practice[] = [
	{
		id: 1,
		slug: "pr-description-quality",
		name: "PR Description Quality",
		category: "code-quality",
		description: "Ensures PR descriptions are detailed and informative.",
		triggerEvents: ["PullRequestCreated", "PullRequestReady"],
		criteria:
			"## PR Description Quality\n\nEvaluate whether the pull request description provides sufficient context, motivation, and testing steps.\n\n### Required Elements\n- Summary of changes\n- Motivation / why\n- Testing steps\n- Link to issue",
		active: true,
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-15"),
	},
	{
		id: 2,
		slug: "code-review-thoroughness",
		name: "Code Review Thoroughness",
		category: "code-quality",
		description: "Evaluates depth and quality of code reviews.",
		triggerEvents: ["ReviewSubmitted"],
		active: true,
		createdAt: new Date("2025-06-02"),
		updatedAt: new Date("2025-06-14"),
	},
	{
		id: 3,
		slug: "test-coverage",
		name: "Test Coverage",
		description: "Checks that new code includes appropriate test coverage.",
		triggerEvents: ["PullRequestCreated", "PullRequestSynchronized"],
		active: false,
		createdAt: new Date("2025-06-03"),
		updatedAt: new Date("2025-06-10"),
	},
];

/** Active practice without category — tests the no-badge rendering path. */
export const mockPracticeNoCategory: Practice = {
	id: 5,
	slug: "error-state-handling",
	name: "Error State Handling",
	description: "Evaluates whether the code properly handles and surfaces errors to the user.",
	triggerEvents: ["PullRequestCreated"],
	active: true,
	createdAt: new Date("2025-06-05"),
	updatedAt: new Date("2025-06-17"),
};

/** Practice with an extremely long name and verbose criteria — tests text overflow. */
export const mockPracticeLongText: Practice = {
	id: 6,
	slug: "very-long-practice-name-to-test-overflow-in-card-layouts",
	name: "Extremely Verbose Practice Name That Tests Text Wrapping and Overflow Behavior in Card Layouts",
	category: "very-long-category-name",
	description:
		"This practice has an unusually long description to verify that the card layout handles multi-line text gracefully without breaking the visual hierarchy or causing horizontal scrolling on smaller viewports. It should wrap naturally and remain readable.",
	triggerEvents: [
		"PullRequestCreated",
		"PullRequestReady",
		"PullRequestSynchronized",
		"ReviewSubmitted",
	],
	criteria:
		"## Very Long Criteria\n\nThis is a multi-paragraph criteria block designed to test the line-clamp behavior on the card preview.\n\n### Section 1\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n\n### Section 2\nUt enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.\n\n### Section 3\nDuis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
	active: true,
	createdAt: new Date("2025-06-06"),
	updatedAt: new Date("2025-06-18"),
};

export const mockPracticeWithAllTriggers: Practice = {
	id: 4,
	slug: "commit-discipline",
	name: "Commit Discipline",
	category: "commit-hygiene",
	description: "Ensures commits follow conventional commit standards with meaningful messages.",
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
	active: true,
	createdAt: new Date("2025-06-04"),
	updatedAt: new Date("2025-06-16"),
};
