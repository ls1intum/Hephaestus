import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeForm } from "./PracticeForm";
import { mockAreas, mockPracticeWithAllTriggers } from "./story-mock-data";

const createSubmit = fn();
const editSubmit = fn();

const meta = {
	title: "Admin/Practices/Practice editor",
	component: PracticeForm,
	parameters: {
		a11y: { test: "error" },
		layout: "fullscreen",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	decorators: [withStandardPage],
} satisfies Meta;

export default meta;
type Story = StoryObj;

export const Create: Story = {
	render: () => (
		<PracticeForm
			mode="create"
			workspaceSlug="demo"
			areas={mockAreas}
			onSubmit={createSubmit}
			onCancel={fn()}
			isPending={false}
		/>
	),
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const EditWithAdvanced: Story = {
	render: () => (
		<PracticeForm
			mode="edit"
			workspaceSlug="demo"
			initialData={mockPracticeWithAllTriggers}
			areas={mockAreas}
			onSubmit={fn()}
			onCancel={fn()}
			isPending={false}
		/>
	),
};

export const Submitting: Story = {
	render: () => (
		<PracticeForm
			mode="create"
			workspaceSlug="demo"
			areas={mockAreas}
			onSubmit={fn()}
			onCancel={fn()}
			isPending
		/>
	),
};

export const EditClearsOptionalGuidance: Story = {
	render: () => (
		<PracticeForm
			mode="edit"
			workspaceSlug="demo"
			initialData={{
				...mockPracticeWithAllTriggers,
				whyItMatters: "Small commits make review safer.",
				whatGoodLooksLike: "Each commit explains one coherent change.",
			}}
			areas={mockAreas}
			onSubmit={editSubmit}
			onCancel={fn()}
			isPending={false}
		/>
	),
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		editSubmit.mockClear();
		await userEvent.clear(canvas.getByRole("textbox", { name: "Why it matters" }));
		await userEvent.clear(canvas.getByRole("textbox", { name: "What good looks like" }));
		await userEvent.click(canvas.getByRole("button", { name: "Save changes" }));

		await expect(editSubmit).toHaveBeenCalledWith(
			"commit-discipline",
			expect.objectContaining({
				clear: ["WHY_IT_MATTERS", "WHAT_GOOD_LOOKS_LIKE"],
			}),
			null,
		);
	},
};

export const ValidationAndSubmit: Story = {
	render: () => (
		<PracticeForm
			mode="create"
			workspaceSlug="demo"
			areas={mockAreas}
			onSubmit={createSubmit}
			onCancel={fn()}
			isPending={false}
		/>
	),
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		createSubmit.mockClear();
		await userEvent.click(canvas.getByRole("button", { name: "Create practice" }));
		await expect(canvas.getByText("Name must be at least 3 characters")).toBeVisible();
		await expect(canvas.getByText("Select at least one trigger event")).toBeVisible();
		await expect(canvas.getByRole("textbox", { name: /Name/ })).toHaveAttribute(
			"aria-invalid",
			"true",
		);

		await userEvent.type(canvas.getByRole("textbox", { name: /Name/ }), "Clear review context");
		await userEvent.click(
			canvas.getByRole("checkbox", { name: "Pull or merge request is opened" }),
		);
		await userEvent.type(
			canvas.getByRole("textbox", { name: /Evaluation criteria/ }),
			"Check whether the reviewed work explains its purpose.",
		);
		await userEvent.click(canvas.getByRole("button", { name: "Create practice" }));
		await expect(createSubmit).toHaveBeenCalledWith(
			{
				name: "Clear review context",
				slug: "clear-review-context",
				criteria: "Check whether the reviewed work explains its purpose.",
				triggerEvents: ["PullRequestCreated"],
				artifactType: "PULL_REQUEST",
			},
			null,
		);
	},
};
