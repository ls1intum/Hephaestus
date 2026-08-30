import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";

import { ConsentBanner } from "./ConsentBanner";

const meta = {
	title: "Surveys/ConsentBanner",
	component: ConsentBanner,
	args: {
		editing: false,
		onAllow: fn(),
		onDecline: fn(),
		onCancel: fn(),
	},
} satisfies Meta<typeof ConsentBanner>;

export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
