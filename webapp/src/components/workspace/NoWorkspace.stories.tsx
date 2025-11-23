import type { Meta, StoryObj } from "@storybook/react";
import { NoWorkspace } from "./NoWorkspace";

const meta: Meta<typeof NoWorkspace> = {
	title: "Workspace/NoWorkspace",
	component: NoWorkspace,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Empty-state screen shown when a user has no workspace membership. Provides a short explanation and a CTA to learn about workspaces.",
			},
		},
	},
	tags: ["autodocs"],
};

export default meta;
type Story = StoryObj<typeof NoWorkspace>;

/**
 * Default presentation with guidance and a link to learn more about workspaces.
 */
export const Default: Story = {};

/**
 * Rendered inside a constrained container to mirror typical page layout usage.
 */
export const NarrowContainer: Story = {
	parameters: {
		layout: "padded",
	},
	decorators: [
		(StoryComponent) => (
			<div className="max-w-md">
				<StoryComponent />
			</div>
		),
	],
};
