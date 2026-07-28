import type { Meta, StoryObj } from "@storybook/react-vite";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewArtifact, ReviewArtifactLink } from "./ReviewArtifact";
import { reviewArtifact } from "./story-mock-data";

const meta = {
	title: "Admin/Practice reviews/Building blocks/Reviewed work",
	component: ReviewArtifact,
	parameters: {
		a11y: { test: "error" },
		layout: "padded",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	args: { artifact: reviewArtifact },
} satisfies Meta<typeof ReviewArtifact>;

export default meta;
type Story = StoryObj<typeof meta>;

export const GitHubPullRequest: Story = { name: "GitHub pull request" };

export const ExternalLink: Story = {
	render: (args) => <ReviewArtifactLink {...args} />,
};

export const GitLabMergeRequest: Story = {
	name: "GitLab merge request",
	args: { artifact: { ...reviewArtifact, provider: "GITLAB", number: 23 } },
};

export const Conversation: Story = {
	args: {
		artifact: {
			id: 81,
			type: "CONVERSATION_THREAD",
			provider: "SLACK",
			channelName: "engineering",
			title: "Architecture review follow-up",
		},
	},
};

export const LongTitle: Story = {
	args: {
		display: "full",
		artifact: {
			...reviewArtifact,
			repositoryName: "hephaestus-administration-and-practice-feedback-platform",
			title:
				"Explain why the contribution changes review delivery policy for every workspace administrator",
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
