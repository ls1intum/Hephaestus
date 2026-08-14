import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewTargetPage } from "./ReviewTargetPage";
import {
	outlineDocument,
	reviewArtifact,
	reviewFeedback,
	reviewObservations,
	slackConversation,
	workspacePractices,
} from "./story-mock-data";

const page = (content: unknown[]) => ({
	content,
	page: { number: 0, size: 5, totalElements: content.length, totalPages: 1 },
});

const handlers = (
	feedback: unknown[] = reviewFeedback.slice(0, 2),
	observations: unknown[] = reviewObservations.slice(0, 2),
) => [
	http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
		HttpResponse.json(page(feedback)),
	),
	http.get("*/workspaces/:workspaceSlug/practices/reviews/observations", () =>
		HttpResponse.json(page(observations)),
	),
	http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(workspacePractices)),
];

const meta = {
	title: "Workspace admin/Practice reviews/Reviewed work",
	component: ReviewTargetPage,
	parameters: {
		layout: "padded",
		msw: { handlers: handlers() },
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
		await canvas.findByRole("heading", { name: reviewArtifact.title, level: 2 });
		// The breadcrumb stops before repeating the heading, and the eyebrow is gone.
		await expect(canvas.queryAllByText("Reviewed work")).toHaveLength(0);
		canvas.getByText("ls1intum/Hephaestus · PR #1423");
		await canvas.findByText(reviewFeedback[0].bodyPreview);
		await canvas.findByText(reviewObservations[0].title);
		await expectNoPageOverflow();
	},
};

/**
 * The same page for a chat thread, which is where the iconography has to hold up: the words say
 * `#engineering` and the mark says Slack.
 */
export const Conversation: Story = {
	args: { artifactKind: "chat.conversation_thread", artifactId: 81 },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: handlers(
				[reviewFeedback[3]],
				[{ ...reviewObservations[4], artifact: slackConversation }],
			),
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: slackConversation.title, level: 2 });
		canvas.getByText("#engineering");
	},
};

/** And for a document, whose label is its kind because it has no number to be known by. */
export const Document: Story = {
	args: { artifactKind: "docs.document", artifactId: 96 },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: handlers(
				[reviewFeedback[4]],
				[{ ...reviewObservations[5], artifact: outlineDocument }],
			),
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: outlineDocument.title, level: 2 });
	},
};

/** Work nothing has reviewed says why that might be, rather than reporting an absence of output. */
export const NothingReviewed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: handlers([], []) },
	},
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
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
					HttpResponse.json(page(reviewFeedback.slice(0, 2))),
				),
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/observations",
					() => new HttpResponse(null, { status: 500 }),
				),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: reviewArtifact.title, level: 2 });
		await canvas.findByText("Couldn't load observations");
		await canvas.findByText(reviewFeedback[0].bodyPreview);
	},
};
