import type { Meta, StoryObj } from "@storybook/react";

import { ComingSoon } from "./ComingSoon";

const meta: Meta<typeof ComingSoon> = {
	title: "Shared/ComingSoon",
	component: ComingSoon,
	parameters: {
		layout: "fullscreen",
	},
	tags: ["autodocs"],
	argTypes: {
		title: {
			control: "text",
			description: "The main heading text",
		},
		description: {
			control: "text",
			description: "The description text below the title",
		},
		attribution: {
			control: "text",
			description: "The attribution or signature text",
		},
	},
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {},
};

export const CustomContent: Story = {
	args: {
		title: "Feature in Development",
		description: "Our team is crafting this feature with care. Check back soon for updates!",
		attribution: "— Engineering Team",
	},
};

export const ShortMessage: Story = {
	args: {
		title: "Almost Ready!",
		description: "Final touches in progress...",
		attribution: "— Dev Team",
	},
};

export const LongMessage: Story = {
	args: {
		title: "Revolutionary AI Feature Coming Soon",
		description:
			"We're building something that will transform how you interact with our platform. Our advanced AI mentor system is currently in the final stages of development and testing.",
		attribution: "— The Innovation Squad",
	},
};
