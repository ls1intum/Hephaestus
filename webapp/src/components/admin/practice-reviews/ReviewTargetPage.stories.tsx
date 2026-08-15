import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewTargetPage } from "./ReviewTargetPage";
import { reviewHandlers } from "./story-mock-server";

// The mock filters by `artifactKind` and `artifactId`, so a story only names the work and gets back
// what the endpoints would return for it. Hand-picked rows can put one work's observation on
// another's page.
const meta = {
	title: "Workspace admin/Practice reviews/Reviewed work",
	component: ReviewTargetPage,
	parameters: {
		// One MSW worker answers a whole Docs page, so each story gets its own frame until MSW goes.
		docs: { story: { inline: false, height: "600px" } },
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

export const PullRequest: Story = {
	parameters: { viewport: { defaultViewport: "reflow" } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", {
			name: "Cache the workspace member lookup on the review path",
			level: 2,
		});
		// Nothing labels the page as "Reviewed work": the breadcrumb stops before the heading, and the
		// work names itself.
		await expect(canvas.queryAllByText("Reviewed work")).toHaveLength(0);
		expect(canvas.getAllByText("ls1intum/Hephaestus · PR #1423").length).toBeGreaterThan(0);
		await canvas.findByText(/2 issues to tighten in this change/);
		await canvas.findByText("A cache miss and a permission failure come back as the same 404");
		await expectNoPageOverflow();
	},
};

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

/** A document's label is its kind, because it has no number to be known by. */
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
