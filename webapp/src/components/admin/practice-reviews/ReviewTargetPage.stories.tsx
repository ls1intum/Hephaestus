import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewTargetPage } from "./ReviewTargetPage";
import { reviewHandlers } from "./story-mock-server";

/**
 * The mock endpoint filters by `artifactKind` and `artifactId`, so each story here only has to name
 * the work: what comes back is what the review endpoints would return for it. The stories used to
 * hand-pick the rows, which is how a document's page came to show a merge request's observation.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Reviewed work",
	component: ReviewTargetPage,
	parameters: {
		layout: "padded",
		msw: { handlers: reviewHandlers() },
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		artifactKind: "scm.pull_request",
		artifactId: 42,
	},
} satisfies Meta<typeof ReviewTargetPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Everything the reviews have said about one pull request.
 *
 * <p>The header used to be a breadcrumb reading "Practice reviews / Reviewed work", a grey
 * "Reviewed work" eyebrow under it, the title, and then a sentence explaining what the page was —
 * four lines to say one thing. The work's own mark and label carry the kind now, which is a fact
 * about this work rather than a label for the page.
 */
export const PullRequest: Story = {
	parameters: { viewport: { defaultViewport: "reflow" } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", {
			name: "Cache the workspace member lookup on the review path",
			level: 2,
		});
		// The breadcrumb stops before repeating the heading, and the eyebrow is gone.
		await expect(canvas.queryAllByText("Reviewed work")).toHaveLength(0);
		// The header names the work once, and every row under it names it again in its meta line.
		expect(canvas.getAllByText("ls1intum/Hephaestus · PR #1423").length).toBeGreaterThan(0);
		await canvas.findByText(/2 issues to tighten in this change/);
		await canvas.findByText("A cache miss and a permission failure come back as the same 404");
		await expectNoPageOverflow();
	},
};

/**
 * The same page for a merge request, where a wrong glyph would show: the words say
 * `platform/billing-service · MR !88` and the mark says GitLab.
 */
export const MergeRequest: Story = {
	args: { artifactId: 43 },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", {
			name: "Move invoice numbering behind the billing boundary",
			level: 2,
		});
		expect(canvas.getAllByText("platform/billing-service · MR !88").length).toBeGreaterThan(0);
	},
};

/**
 * The same page for a chat thread, which is where the iconography has to hold up: the words say
 * `#engineering` and the mark says Slack.
 */
export const Conversation: Story = {
	args: { artifactKind: "chat.conversation_thread", artifactId: 81 },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", {
			name: "How should we roll back the pricing migration?",
			level: 2,
		});
		expect(canvas.getAllByText("#engineering").length).toBeGreaterThan(0);
		await canvas.findByText("The thread ends without naming what was chosen");
	},
};

/** And for a document, whose label is its kind because it has no number to be known by. */
export const Document: Story = {
	args: { artifactKind: "docs.document", artifactId: 96 },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", {
			name: "Runbook: restoring a workspace from backup",
			level: 2,
		});
		await canvas.findByText("The runbook opens with the one step that cannot be undone");
	},
};

/** Work nothing has reviewed says why that might be, rather than reporting an absence of output. */
export const NothingReviewed: Story = {
	args: { artifactId: 45 },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByText("Nothing has been reviewed on this work");
	},
};

/** One section failing does not take the other down with it. */
export const ObservationsFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/observations",
					() => new HttpResponse(null, { status: 500 }),
				),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", {
			name: "Cache the workspace member lookup on the review path",
			level: 2,
		});
		await canvas.findByText("Couldn't load observations");
		await canvas.findByText(/2 issues to tighten in this change/);
	},
};
