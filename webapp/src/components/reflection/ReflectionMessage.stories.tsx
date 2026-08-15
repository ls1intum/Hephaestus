import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { withStandardPage } from "@/stories/decorators";
import { ReflectionMessage } from "./ReflectionMessage";
import {
	feedbackWithAwkwardMarkdown,
	feedbackWithLearnerFraming,
	feedbackWithoutABody,
	feedbackWithoutAnObservationTitle,
	feedbackWithoutFraming,
} from "./story-mock-data";

/**
 * One pattern on the reflection surface: what recurs, the practice it belongs to, the practice's own
 * words, the work it was seen on, and — as the last thing the composed text says — one habit to try
 * next time.
 *
 * The stories below are the shapes the server can actually send: a practice with no area, an
 * occurrence the review recorded without a title, Markdown a model wrote, and a unit whose text did
 * not survive composition.
 */
const meta = {
	title: "My feedback/One pattern",
	component: ReflectionMessage,
	parameters: { layout: "fullscreen" },
	// The message is a row inside the page's one bordered container, so it is shown in one here
	// rather than floating: a card each is exactly the shape this screen was asked not to be.
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-3xl rounded-lg border">
				<Story />
			</div>
		),
		withStandardPage,
	],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		feedback: feedbackWithLearnerFraming,
	},
} satisfies Meta<typeof ReflectionMessage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { level: 3, name: "Tests are arriving one commit late" }),
		).toBeVisible();

		// The practice is named, and named as a practice: a headline about a habit with no practice
		// behind it would be an opinion.
		await expect(canvas.getByText(/About the practice/)).toBeVisible();
		await expect(canvas.getByText("Ship the test with the change")).toBeVisible();
		await expect(canvas.getByText(/in Testing\./)).toBeVisible();

		await expect(canvas.getByText("Why this matters")).toBeVisible();
		await expect(canvas.getByText("What good looks like")).toBeVisible();
		await expect(canvas.getByText("Try next:")).toBeVisible();
		await expect(canvas.getByText("Seen on 3 pieces of your work")).toBeVisible();
	},
};

/**
 * A practice in no area, with no learner framing recorded. Both clauses disappear rather than
 * leaving an empty label or a dangling comma, and the message is still complete without them.
 */
export const WithoutAnAreaOrLearnerFraming: Story = {
	args: { feedback: feedbackWithoutFraming },
	play: async ({ canvas }) => {
		await expect(await canvas.findByText(/About the practice/)).toBeVisible();
		await expect(canvas.queryByText(/in Testing/)).not.toBeInTheDocument();
		await expect(canvas.queryByText("Why this matters")).not.toBeInTheDocument();
		await expect(canvas.queryByText("What good looks like")).not.toBeInTheDocument();
		await expect(canvas.getByText("Try next:")).toBeVisible();
	},
};

/** An occurrence the review recorded without a title falls back to naming the kind of work. */
export const AnOccurrenceWithoutATitle: Story = {
	args: { feedback: feedbackWithoutAnObservationTitle },
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("Seen on 2 pieces of your work")).toBeVisible();
		await expect(canvas.getByRole("link", { name: "Pull or merge request" })).toBeVisible();
		await expect(
			canvas.getByRole("link", {
				name: "Pull or merge request: Rename and behaviour change land together across 41 files",
			}),
		).toBeVisible();
	},
};

/**
 * Markdown a model wrote is rendered with no HTML passthrough, no remote images, and no link the
 * renderer has not checked: a `javascript:` href becomes its own text, an image is dropped, and a
 * fenced block wider than the container wraps instead of becoming a scroll region no keyboard can
 * reach.
 *
 * Its headings are demoted to `h4` as well, so a composed message cannot outrank the heading of the
 * pattern it belongs to.
 */
export const MarkdownFromTheComposer: Story = {
	args: { feedback: feedbackWithAwkwardMarkdown },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { level: 4, name: "Where this shows up" }),
		).toBeVisible();
		await expect(canvas.getByRole("link", { name: "the testing guide" })).toHaveAttribute(
			"href",
			"https://example.com/testing",
		);
		await expect(canvas.queryByRole("link", { name: "the practice" })).not.toBeInTheDocument();
		await expect(canvas.getByText("the practice")).toBeVisible();
		await expect(canvas.queryByRole("img")).not.toBeInTheDocument();
	},
};

/** Facts without words. Saying so beats a heading floating above evidence with nothing between. */
export const WithoutAComposedBody: Story = {
	args: { feedback: feedbackWithoutABody },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { level: 3, name: "Ship the test with the change" }),
		).toBeVisible();
		await expect(canvas.getByText(/No feedback text was composed for this pattern/)).toBeVisible();
		await expect(canvas.getByText("Seen on 3 pieces of your work")).toBeVisible();
	},
};
