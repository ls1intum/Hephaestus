import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { expectNoOverflowingElement } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { mockPullRequestItemType } from "./fixtures";
import { PracticeAppliesWhenEditor } from "./PracticeAppliesWhenEditor";

const meta = {
	component: PracticeAppliesWhenEditor,
	args: {
		id: "applies-when",
		itemType: mockPullRequestItemType,
		value: {
			clauses: [{ changedPath: ["**/*.swift"] }],
			otherwise: "The change has no Swift code.",
		},
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	render: (args) => (
		<Stateful initial={args.value}>
			{(value, setValue) => (
				<PracticeAppliesWhenEditor {...args} value={value} onChange={setValue} />
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeAppliesWhenEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("combobox", { name: "Condition 1" })).toHaveTextContent(
			"A file changed",
		);
		await expect(canvas.getByRole("textbox", { name: "File paths 1" })).toHaveValue("**/*.swift");
	},
};

export const Empty: Story = {
	args: { value: { clauses: [], otherwise: "" } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Add condition" }));
		await userEvent.type(canvas.getByRole("textbox", { name: "File paths 1" }), "src/**");
		await userEvent.click(canvas.getByRole("button", { name: "Remove condition 1" }));
		await expect(canvas.queryByRole("textbox")).toBeNull();
	},
};

export const Loading: Story = { args: { disabled: true } };
export const Error: Story = { args: { error: "The conditions could not be saved. Try again." } };

export const DifferentCondition: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Condition 1" }));
		const options = within(await screen.findByRole("listbox", { name: "Condition 1" }));
		await userEvent.click(options.getByRole("option", { name: "The diff contains text" }));
		await userEvent.type(canvas.getByRole("textbox", { name: "Text to find 1" }), "TODO");
		await expect(canvas.queryByRole("textbox", { name: "File paths 1" })).toBeNull();
		await expectNoOverflowingElement(canvas.getByRole("group", { name: "Applies when" }));
	},
};
