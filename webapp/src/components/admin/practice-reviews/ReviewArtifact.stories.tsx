import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewArtifactLabel, ReviewArtifactLink } from "./ReviewArtifact";
import {
	gitlabMergeRequest,
	outlineDocument,
	reviewArtifact,
	slackConversation,
} from "./story-mock-data";

/**
 * Which piece of work a record is about, in one line.
 *
 * <p>The glyph is the forge, not the kind: the words already say `PR #1423` or `#engineering`, so a
 * second pull-request icon would repeat them and leave a GitHub request indistinguishable from a
 * GitLab one. These are the same four brand marks the integrations console and the sidebar draw.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Reviewed work",
	component: ReviewArtifactLabel,
	parameters: { layout: "padded", chromatic: { viewports: [1440] } },
	tags: ["autodocs"],
	args: { artifact: reviewArtifact },
} satisfies Meta<typeof ReviewArtifactLabel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const GitHubPullRequest: Story = { name: "GitHub pull request" };

export const GitLabMergeRequest: Story = {
	name: "GitLab merge request",
	args: { artifact: gitlabMergeRequest },
	play: async ({ canvas }) => {
		// GitLab calls it a merge request and numbers it with a bang; the words follow the forge.
		canvas.getByText("platform/billing-service · MR !88");
	},
};

export const SlackConversation: Story = { args: { artifact: slackConversation } };

export const OutlineDocument: Story = { args: { artifact: outlineDocument } };

/**
 * As a link out to the forge, with the affordance on the label alone.
 *
 * Hovering this used to underline the work's *title* as well, because the anchor wrapped both and
 * carried `hover:underline`. The anchor now contains the label and nothing else, and a caller that
 * wants the title renders it outside — which is what every detail screen does.
 */
export const ExternalLink: Story = {
	render: (args) => (
		<div className="space-y-1">
			<ReviewArtifactLink {...args} />
			<p className="text-sm text-muted-foreground">{args.artifact?.title}</p>
		</div>
	),
	play: async ({ canvas }) => {
		const link = canvas.getByRole("link");
		await expect(link).toHaveAttribute("target", "_blank");
		await expect(canvas.getByText(reviewArtifact.title).closest("a")).toBeNull();
	},
};

/** A work item with no URL recorded is named, not linked — and never rendered as a dead anchor. */
export const WithoutAUrl: Story = {
	render: (args) => <ReviewArtifactLink {...args} />,
	args: { artifact: { ...reviewArtifact, url: undefined } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("link")).not.toBeInTheDocument();
	},
};

export const LongTitle: Story = {
	args: {
		artifact: {
			...reviewArtifact,
			repositoryName: "hephaestus-administration-and-practice-feedback-platform",
		},
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const Unresolved: Story = { args: { artifact: undefined } };
