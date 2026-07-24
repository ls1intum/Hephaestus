import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PriceModeEditor } from "./PriceModeEditor";

const meta = {
	component: PriceModeEditor,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		audience: "instance",
		idPrefix: "story-price",
		value: { pricingMode: "PRICED", per1mInputUsd: 3, per1mOutputUsd: 15 },
		onChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-md">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PriceModeEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Instance admin: priced model, shows the per-1M rate inputs. */
export const InstancePriced: Story = {};

/** Instance admin: an intentional no-charge declaration, with the required note field. */
export const InstanceNoCharge: Story = {
	args: { value: { pricingMode: "NO_CHARGE", note: "self-hosted, no cost" } },
};

/** Instance admin: no price set yet. */
export const InstanceUnpriced: Story = {
	args: { value: { pricingMode: "UNPRICED" } },
};

/** Workspace admin: the provider has no metered API rate. */
export const WorkspaceNoCharge: Story = {
	args: {
		audience: "workspace",
		value: { pricingMode: "NO_CHARGE", note: "self-hosted, no cost" },
	},
};

export const WorkspaceUnpriced: Story = {
	args: { audience: "workspace", value: { pricingMode: "UNPRICED" } },
};

export const ValidationErrors: Story = {
	args: {
		value: { pricingMode: "PRICED" },
		errors: {
			per1mInputUsd: "Required when the model has a price.",
			per1mOutputUsd: "Required when the model has a price.",
		},
	},
};
