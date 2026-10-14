import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import type { EvidenceCitation } from "@/api/types.gen";
import { knownEvidenceSourceKinds } from "@/components/practice-vocabulary/evidence-source-defs";
import { expectNoPageOverflow } from "@/stories/reflow";

import { ResultEvidence } from "./ResultEvidence";

const CITATION_BY_KIND: Record<string, { path: string; quote: string }> = {
	"scm.pull-request.core": {
		path: "Pull request description",
		quote: "Adds the admin read surface for review output. Follow-up to #1402.",
	},
	"scm.pull-request.diff": {
		path: "webapp/src/components/admin/practice-reviews/ReviewRow.tsx",
		quote:
			'const routeName = "detection-output";\nreturn <Link to={routeName}>{entry.title}</Link>;',
	},
	"scm.pull-request.comments": {
		path: "Comment by @grace",
		quote: "Can we name this after what an operator is looking for rather than the pipeline stage?",
	},
	"scm.repository.tree": {
		path: "webapp/src/lib/item-kinds.ts",
		quote: 'export const ITEM_KIND = {\n\tpullRequest: "scm.pull_request",\n};',
	},
	"scm.issue.core": {
		path: "Issue #1430",
		quote: "Operators cannot tell which reviews produced feedback and which were withheld.",
	},
	"scm.issue.comments": {
		path: "Comment by @ada",
		quote: "The wording here still uses the pipeline term — we agreed on results.",
	},
	"docs.document.core": {
		path: "Reviewing practices",
		quote: "Feedback is addressed to the author, in the second person, and names one change.",
	},
	"slack.conversation.thread": {
		path: "Message from Ada Lovelace",
		quote: "I renamed the route but left the old one redirecting, so nobody's bookmark breaks.",
	},
	"scm.linked-work-items": {
		path: "Issue #1402",
		quote: "Blocked by: the read model must expose the withholding reason.",
	},
	"scm.review-threads": {
		path: "Thread on ReviewRow.tsx",
		quote: "This row is doing two jobs — can the table and the card be one component?",
	},
	"scm.general-review-comments": {
		path: "Review by @grace",
		quote: "Approving once the terminology sweep lands.",
	},
	"workspace.project-inventory": {
		path: "webapp",
		quote: "React 19, TanStack Router, Tailwind 4, Vitest, Storybook 10.",
	},
	"outline.documents": {
		path: "Frontend conventions",
		quote: "Prefer one responsive component over a table and a card list.",
	},
	"hephaestus.result-history": {
		path: "Result from 3 July",
		quote: "The route exposed an internal pipeline term; raised then, and unchanged since.",
	},
	"hephaestus.feedback-history": {
		path: "Feedback sent on 3 July",
		quote: "You named the boundary after the storage model rather than the product concept.",
	},
};

function citation(sourceKind: string, overrides: Partial<EvidenceCitation> = {}): EvidenceCitation {
	const sample = CITATION_BY_KIND[sourceKind];
	if (!sample) {
		throw new Error(`No sample passage is written for evidence source ${sourceKind}`);
	}
	const { path, quote } = sample;
	const isDiff = sourceKind === "scm.pull-request.diff";
	return {
		sourceKind,
		sourcePath: `inputs/context/${sourceKind}.json`,
		path,
		quote,
		startLine: 12,
		endLine: quote.includes("\n") ? 13 : 12,
		side: isDiff ? "NEW" : null,
		revision: null,
		verification: null,
		...overrides,
	};
}

const meta = {
	component: ResultEvidence,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: {
		citations: [
			citation("scm.pull-request.diff"),
			citation("scm.pull-request.diff", {
				path: "webapp/src/components/admin/practice-reviews/ResultRows.tsx",
				side: "OLD",
				quote: '<TableHead scope="col">Verdict</TableHead>',
				startLine: 82,
				endLine: 82,
			}),
			citation("scm.review-threads"),
		],
	},
} satisfies Meta<typeof ResultEvidence>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("3 passages from 2 sources.");
		canvas.getByRole("heading", { name: "The code changes", level: 4 });
		canvas.getByRole("heading", { name: "Review threads on the code", level: 4 });
		canvas.getByText("webapp/src/components/admin/practice-reviews/ReviewRow.tsx:12–13");
		canvas.getByText("Before");
		await expect(canvas.queryByText("scm.pull-request.diff")).not.toBeInTheDocument();
	},
};

// Keep expected labels independent of the registry so missing labels cannot agree with themselves.
export const EverySource: Story = {
	args: {
		citations: knownEvidenceSourceKinds().map((kind) => citation(kind)),
	},
	play: async ({ canvas }) => {
		await expect(
			canvas
				.getAllByRole("heading", { level: 4 })
				.map((heading) => heading.textContent)
				.sort(),
		).toEqual(
			[
				"The pull request itself",
				"The code changes",
				"Comments on the pull request",
				"Files and history in the repository",
				"The issue itself",
				"Comments on the issue",
				"The document itself",
				"The conversation",
				"Linked issues and requests",
				"Review threads on the code",
				"Review comments",
				"What this project contains",
				"Referenced documents",
				"Earlier results",
				"Feedback already sent",
			].sort(),
		);
		await expect(canvas.queryByText(/Message from Ada Lovelace:\d/u)).not.toBeInTheDocument();
	},
};

// Object coordinates identify serialized context; only code locators expose source line numbers.
export const LineNumbersOnlyWhereTheyAreReal: Story = {
	args: {
		citations: [citation("scm.repository.tree"), citation("slack.conversation.thread")],
	},
	play: async ({ canvas }) => {
		canvas.getByText("webapp/src/lib/item-kinds.ts:12–13");
		canvas.getByText("Message from Ada Lovelace");
		await expect(canvas.queryByText(/Message from Ada Lovelace:12/u)).not.toBeInTheDocument();
	},
};

export const RedactedQuote: Story = {
	args: {
		citations: [citation("scm.pull-request.diff", { quote: null })],
	},
	play: async ({ canvas, canvasElement }) => {
		canvas.getByText("Not quoted. The passage was withheld, so only its location was kept.");
		canvas.getByText("webapp/src/components/admin/practice-reviews/ReviewRow.tsx:12–13");
		await expect(canvasElement.querySelector("pre")).toBeNull();
	},
};

export const UnknownSource: Story = {
	args: {
		citations: [
			{
				sourceKind: "wiki.page.body",
				sourcePath: "inputs/context/wiki.json",
				path: "Onboarding",
				quote: "New reviewers shadow an existing reviewer for their first week.",
				startLine: 4,
				endLine: 4,
				side: null,
				revision: null,
				verification: null,
			},
		],
	},
	play: async ({ canvas }) => {
		canvas.getByRole("heading", { name: "Another source", level: 4 });
		canvas.getByText("A source this version of the app has no description for.");
		await expect(canvas.queryByText(/wiki\.page/u)).not.toBeInTheDocument();
	},
};

export const NoEvidence: Story = {
	args: { citations: [] },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("Nothing was quoted, so there is no passage to check this against."),
		).toBeVisible();
		await expect(canvas.queryByRole("heading")).not.toBeInTheDocument();
	},
};

export const Mobile: Story = {
	args: {
		citations: [
			citation("scm.pull-request.diff", {
				path: "webapp/src/routes/_authenticated/w/$workspaceSlug/admin/practices/reviews/items/$itemId/feedback.tsx",
				quote:
					"const detectionOutputRouteNameThatIsMuchLongerThanAnyMobileViewport = buildRouteName();",
			}),
		],
	},
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		canvas.getByRole("heading", { name: "The code changes", level: 4 });
		await expectNoPageOverflow();
	},
};

export const HistoricalSource: Story = {
	args: {
		citations: [citation("scm.repository.tree", { revision: "b".repeat(40) })],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("b".repeat(40))).toBeVisible();
	},
};

export const WithoutALocation: Story = {
	args: {
		citations: [citation("scm.pull-request.diff", { path: null, startLine: null, endLine: null })],
	},
	play: async ({ canvas }) => {
		canvas.getByText("Location not recorded");
		await expect(canvas.queryByText(/:12/u)).not.toBeInTheDocument();
	},
};
