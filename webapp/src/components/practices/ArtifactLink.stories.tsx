import type { Meta, StoryObj } from "@storybook/react-vite";
import { ArtifactLink } from "@/components/practices/ArtifactLink";
import { MY_REPORT_CARDS } from "@/components/practices/story-data";

/**
 * The one artifact reference treatment on every practice surface: provider state icon,
 * deep-linked title, then repo, number and relative time in muted text. Every practice signal
 * stays anchored to the concrete PR or issue it came from.
 */
const meta = {
	component: ArtifactLink,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { item: MY_REPORT_CARDS[0].toWorkOn[0] },
} satisfies Meta<typeof ArtifactLink>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A merged pull request with repo, number and relative time. */
export const Default: Story = {};

/** An issue reference. */
export const Issue: Story = {
	args: { item: MY_REPORT_CARDS[4].strengths[0] },
};

/** An artifact without a linkable page renders a plain label instead of a dead link. */
export const WithoutLink: Story = {
	args: {
		item: {
			...MY_REPORT_CARDS[0].toWorkOn[0],
			artifactType: "CONVERSATION_THREAD",
			artifactTitle: undefined,
			artifactUrl: undefined,
			artifactNumber: undefined,
			artifactRepository: undefined,
			artifactState: undefined,
		},
	},
};

/** A very long artifact title truncates instead of wrapping the row. */
export const LongTitle: Story = {
	render: (args) => (
		<div className="w-80">
			<ArtifactLink {...args} />
		</div>
	),
	args: {
		item: {
			...MY_REPORT_CARDS[0].toWorkOn[0],
			artifactTitle:
				"Rework the entire payment webhook retry pipeline including signature verification, backoff and dead-letter handling",
		},
	},
};
