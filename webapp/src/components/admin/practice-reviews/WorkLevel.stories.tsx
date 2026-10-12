import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { artifactTrace, tracedArtifact } from "@/components/practice-trace/fixtures";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { settledDrawerPanel } from "@/stories/overlay";

import { mergedPullRequestFacts, openIssueFacts } from "./fixtures";
import { SHEET_SECTIONS } from "./RecordSheet";
import { WorkLevel } from "./WorkLevel";

/**
 * One piece of work's sheet, in the order every record sheet has: a status line (whether a review
 * started, where it stands on its provider, how many moments, the latest), What it is (kind,
 * author, branches, size, checks), its history — opened, every moment on the work with what
 * happened, how we came to know, why no review started and the setting that would let it through,
 * merged or closed — Technical (closed) and asking for a review as the footer.
 */
const meta = {
	component: WorkLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		work: { status: "ready", work: tracedArtifact(1430) },
		onRequestReview: fn(),
	},
	argTypes: { path: { control: false } },
	render: (args) => (
		<InLevelStack entry={{ kind: "work", id: "issue:1430" }} path={args.path}>
			{(level) => <WorkLevel {...args} {...level} />}
		</InLevelStack>
	),
} satisfies Meta<typeof WorkLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NoReviewStarted: Story = {
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await expect(
			panel.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent),
		).toStrictEqual([SHEET_SECTIONS.what, SHEET_SECTIONS.state, SHEET_SECTIONS.technical]);
		// Said once: the status line, not a badge, a field and a heading.
		await expect(panel.getAllByText("No review started")).toHaveLength(1);
		panel.getByText(/This workspace’s review settings did not allow a review of this work\./u);
		await expect(panel.getByRole("link", { name: "Open Review: When and where" })).toHaveAttribute(
			"href",
			expect.stringContaining("/admin/practices/review"),
		);
		await userEvent.click(panel.getByRole("button", { name: "Request review" }));
		// It starts an agent job, so it asks first.
		await expect(args.onRequestReview).not.toHaveBeenCalled();
		const confirm = await screen.findByRole("alertdialog");
		await userEvent.click(within(confirm).getByRole("button", { name: "Request review" }));
		await expect(args.onRequestReview).toHaveBeenCalledOnce();
	},
};

/** While a review of the work is queued or running, the footer says so and asks nothing. */
export const RequestBlockedWhileRunning: Story = {
	args: { requestBlocked: "A review of this work is already queued or running." },
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		const button = panel.getByRole("button", { name: "Request review" });
		await expect(button).toHaveAttribute("aria-disabled", "true");
		await userEvent.click(button);
		await expect(args.onRequestReview).not.toHaveBeenCalled();
	},
};

/**
 * Every moment on the work, oldest first, between when it was opened and merged, and what its
 * provider reported about it: facts of the work, said here.
 */
export const WithMoments: Story = {
	args: {
		work: { status: "ready", work: tracedArtifact(1423) },
		moments: { status: "ready", moments: artifactTrace.signals },
		facts: { status: "ready", facts: mergedPullRequestFacts },
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		const history = within(panel.getByRole("list", { name: "History" }));
		// The moments, opened, merged, and the last update after the merge.
		await expect(history.getAllByRole("listitem")).toHaveLength(artifactTrace.signals.length + 3);
		history.getByText("Opened");
		history.getByText("Merged");
		// Merged once: the flag in the status line, then the event.
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(/Merged/u);
		panel.getByText("Jane Doe");
		panel.getByText("feature/retry-service");
		panel.getByText("+120 −30 · 7 files · 3 commits");
		panel.getByText("Checks failed");
		panel.getByText("Head revision");
	},
};

/** An issue has an author and times, and no branches, size or checks. */
export const IssueFacts: Story = {
	args: { facts: { status: "ready", facts: openIssueFacts } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Alex Smith");
		await expect(panel.queryByText("Branches")).toBeNull();
		await expect(panel.queryByText("Checks")).toBeNull();
	},
};

/** What the provider reported could not be read: the moments still stand. */
export const ProviderFactsFailed: Story = {
	args: { facts: { status: "error", error: new Error("Network down"), onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/what the provider reported about this work/u);
		panel.getByRole("list", { name: "History" });
	},
};

/**
 * Work in a repository the workspace does not monitor: nothing is stored about it, and the sheet
 * says so instead of an error, with what the server answered on a quiet line under it, while the
 * moments still stand.
 */
export const RepositoryNotMonitored: Story = {
	args: {
		facts: {
			status: "ready",
			facts: undefined,
			unmonitored: true,
			answer: "204 No Content from GET /workspaces/demo/practices/reviews/work/scm.issue/1430",
		},
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByText(/workspace does not monitor the repository/u)).toBeVisible();
		await expect(panel.getByText(/^Server answer: 204 No Content from GET /u)).toBeVisible();
		await expect(panel.queryByRole("alert")).toBeNull();
		panel.getByRole("list", { name: "History" });
	},
};

/** Work nothing was recorded about says so, under a title that says what it is. */
export const NothingRecorded: Story = {
	args: { work: { status: "ready", work: undefined } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/Nothing was recorded about this work/u);
		await expect(panel.getByRole("heading", { level: 2 })).toHaveTextContent("Unknown work");
	},
};
