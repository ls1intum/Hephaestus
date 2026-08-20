import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent } from "storybook/test";
import { DeliveryPolicyTrace } from "./DeliveryPolicyTrace";
import { artifactTrace } from "./story-mock-data";

const meta = {
	title: "Workspace admin/Practices/Trace/Delivery policy",
	component: DeliveryPolicyTrace,
	tags: ["autodocs"],
	args: { evaluations: artifactTrace.deliveryPolicy },
} satisfies Meta<typeof DeliveryPolicyTrace>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Denied: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByText("Technical delivery policy trace"));
		await expect(canvas.getByText("Stopped because: Recipient opted out")).toBeVisible();
		await expect(canvas.getByText("Denied")).toBeVisible();
	},
};
