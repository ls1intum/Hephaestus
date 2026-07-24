import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { AvailableLlmModel } from "@/api/types.gen";
import { ModelPicker } from "./ModelPicker";

const mockModels: AvailableLlmModel[] = [
	{
		id: 1,
		scope: "SHARED",
		displayName: "GPT-5",
		connectionDisplayName: "OpenAI production",
		pricingMode: "PRICED",
		per1mInputUsd: 3,
		per1mOutputUsd: 15,
		supportsReasoning: true,
	},
	{
		id: 2,
		scope: "SHARED",
		displayName: "Local Llama (self-hosted)",
		connectionDisplayName: "On-prem GPU",
		pricingMode: "NO_CHARGE",
		supportsReasoning: false,
	},
	{
		id: 10,
		scope: "WORKSPACE",
		displayName: "My OpenAI key",
		connectionDisplayName: "My provider",
		pricingMode: "UNPRICED",
		supportsReasoning: true,
	},
];

const meta = {
	component: ModelPicker,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		availableModels: mockModels,
		value: null,
		onChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="w-80">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof ModelPicker>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const SharedSelected: Story = {
	args: { value: { scope: "SHARED", id: 1 } },
};

export const OwnProviderSelected: Story = {
	args: { value: { scope: "WORKSPACE", id: 10 } },
};

export const NoModelsYet: Story = {
	args: { availableModels: [] },
};

export const OpensAndListsGroups: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox"));
		// The popup renders in a portal → query the document. `toBeInTheDocument` (not `toBeVisible`):
		// the popup's enter transition can still be animating opacity when this assertion runs.
		await expect(await screen.findByRole("option", { name: /GPT-5/ })).toBeInTheDocument();
		await expect(await screen.findByRole("option", { name: /My OpenAI key/ })).toBeInTheDocument();
	},
};
