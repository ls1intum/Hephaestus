import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { FacetMultiSelect } from "./FacetMultiSelect";

const eventTypes = [
	{ value: "LOGIN_SUCCESS", label: "Sign-in succeeded" },
	{ value: "LOGIN_FAILURE", label: "Sign-in failed" },
	{ value: "ROLE_CHANGED", label: "Role changed" },
	{ value: "FEATURE_FLAG_CHANGED", label: "Feature flag changed" },
];

/** One facet of a filter or a form: the toolbar's dashed chip, or a full-width form field. */
const meta = {
	component: FacetMultiSelect,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		title: "Event",
		options: eventTypes,
		selected: [],
		onChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="w-72">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof FacetMultiSelect<string>>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Empty: Story = {};

export const TwoSelected: Story = {
	args: { selected: ["LOGIN_SUCCESS", "ROLE_CHANGED"] },
};

/** Past two, chips would wrap the toolbar, so the trigger collapses to a count. */
export const ManySelected: Story = {
	args: { selected: ["LOGIN_SUCCESS", "LOGIN_FAILURE", "ROLE_CHANGED"] },
};

export const FieldVariant: Story = {
	args: {
		variant: "field",
		title: "Workspaces",
		options: [
			{ value: "10", label: "Teaching team", description: "teaching" },
			{ value: "11", label: "Research team", description: "research" },
			{ value: "12", label: "Ärztliche Fortbildung", description: "aerzte" },
		],
	},
};

/** The caller names the void; the generic "No matches" would be wrong here. */
export const NoOptions: Story = {
	args: { variant: "field", title: "Workspaces", options: [], emptyLabel: "No workspaces yet" },
};

/** "arztliche" finds "Ärztliche", which a `toLowerCase().includes()` filter never would. */
export const AccentInsensitiveSearch: Story = {
	args: {
		variant: "field",
		title: "Workspaces",
		options: [
			{ value: "10", label: "Teaching team", description: "teaching" },
			{ value: "12", label: "Ärztliche Fortbildung", description: "aerzte" },
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox", { name: "Workspaces" }));
		// By placeholder: Base UI's combobox input is itself `role="combobox"`, not a textbox.
		await userEvent.type(await screen.findByPlaceholderText("Search…"), "arztliche");
		await expect(await screen.findByRole("option", { name: /Ärztliche/ })).toBeInTheDocument();
		await expect(screen.queryByRole("option", { name: /Teaching/ })).toBeNull();
	},
};
