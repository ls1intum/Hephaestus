import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, waitFor } from "storybook/test";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormErrorSummary } from "./FormErrorSummary";

/**
 * On a form taller than the viewport, inline errors alone leave the reader hunting: they press the
 * action, nothing visible happens, and the message is a screenful away. The summary is the thing
 * that makes a refused submit legible without scrolling.
 */
const meta = {
	component: FormErrorSummary,
	parameters: { layout: "padded" },
	args: {
		errors: [
			{
				fieldId: "practice-name",
				message: "Give the practice a name of at least three characters.",
			},
			{ fieldId: "practice-criteria", message: "Say what this practice checks." },
		],
	},
	decorators: [
		(Story) => (
			<div className="space-y-6">
				<Story />
				<div className="space-y-2">
					<Label htmlFor="practice-name">Name</Label>
					<Input id="practice-name" />
					<Label htmlFor="practice-criteria">What to look for</Label>
					<Input id="practice-criteria" />
				</div>
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof FormErrorSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		// A live region, so it is announced without stealing the caret out of a field being typed in.
		await expect(canvas.getByRole("alert")).toBeVisible();
		await expect(canvas.getByRole("heading", { name: "There are 2 problems" })).toBeVisible();
	},
};

export const EntryMovesFocusToItsField: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("link", { name: /Give the practice a name/ }));
		// A frame later, because an entry may first have to reveal a collapsed section for its field
		// to exist at all.
		await waitFor(() => expect(canvas.getByLabelText("Name")).toHaveFocus());
	},
};

export const EntryRevealsACollapsedSectionFirst: Story = {
	args: {
		errors: [
			{
				fieldId: "hidden-field",
				message: "The identifier must be lowercase letters, numbers and hyphens.",
				reveal: fn(),
			},
		],
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("link", { name: /identifier/ }));
		// Without this the entry links to an id that is not in the document, and focus stays on the
		// link — worse than offering no link.
		await expect(args.errors[0].reveal).toHaveBeenCalledOnce();
	},
};

export const SingleProblem: Story = {
	args: { errors: [{ fieldId: "practice-name", message: "Give the practice a name." }] },
	play: async ({ canvas }) => {
		// Singular, because "There are 1 problems" is how a form tells you it was written by a machine.
		await expect(canvas.getByRole("heading", { name: "There is a problem" })).toBeVisible();
	},
};

export const NoProblems: Story = {
	args: { errors: [] },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("alert")).not.toBeInTheDocument();
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
