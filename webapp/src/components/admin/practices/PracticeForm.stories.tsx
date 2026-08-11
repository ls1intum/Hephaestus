import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeForm } from "./PracticeForm";
import { mockAreas, mockPracticeWithAllTriggers } from "./story-mock-data";

const createSubmit = fn();
const editSubmit = fn();

const meta = {
	title: "Workspace admin/Practices/Practice editor",
	component: PracticeForm,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	decorators: [withStandardPage],
	args: {
		mode: "create",
		workspaceSlug: "demo",
		areas: mockAreas,
		definitionOptions: mockPracticeDefinitionOptions,
		onSubmit: createSubmit,
		isPending: false,
	},
} satisfies Meta<typeof PracticeForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const EditWithAdvanced: Story = {
	args: { mode: "edit", initialData: mockPracticeWithAllTriggers, onSubmit: fn() },
};

export const Submitting: Story = {
	args: { isPending: true, onSubmit: fn() },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("textbox", { name: /Name/ })).toBeDisabled();
	},
};

export const EditClearsOptionalGuidance: Story = {
	args: {
		mode: "edit",
		initialData: {
			...mockPracticeWithAllTriggers,
			whyItMatters: "Small commits make review safer.",
			whatGoodLooksLike: "Each commit explains one coherent change.",
		},
		onSubmit: editSubmit,
	},
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

export const ValidationErrors: Story = {
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Create practice" }));
		await expect(canvas.getByText("Name must be at least 3 characters")).toBeVisible();
		await expect(canvas.queryByText("Select at least one trigger event")).not.toBeInTheDocument();
		await expect(canvas.getByRole("textbox", { name: /Name/ })).toHaveAttribute(
			"aria-invalid",
			"true",
		);
	},
};

export const ValidationAndSubmit: Story = {
	...ValidationErrors,
	parameters: { chromatic: { disableSnapshot: true } },
	play: async (context) => {
		createSubmit.mockClear();
		await ValidationErrors.play?.(context);
		const { canvas, userEvent } = context;
		await userEvent.type(canvas.getByRole("textbox", { name: /Name/ }), "Clear review context");
		await userEvent.type(
			canvas.getByRole("textbox", { name: /What to look for/ }),
			"Check whether the reviewed work explains its purpose.",
		);
		await userEvent.click(canvas.getByRole("button", { name: "Create practice" }));
		await expect(createSubmit).toHaveBeenCalledWith(
			{
				name: "Clear review context",
				slug: "clear-review-context",
				criteria: "Check whether the reviewed work explains its purpose.",
				bindings: [
					{
						signals: [
							"scm.pull_request.opened",
							"scm.pull_request.ready",
							"scm.pull_request.synchronized",
						],
						needs: mockPracticeDefinitionOptions.workTypes[0].recommendedNeeds,
					},
				],
				automatedReviewPolicy: mockPracticeDefinitionOptions.workTypes[0].recommendedPolicy,
			},
			null,
		);
	},
};

export const ConversationPractice: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		createSubmit.mockClear();
		await userEvent.type(canvas.getByRole("textbox", { name: /Name/ }), "Helpful discussion");
		await userEvent.click(canvas.getByRole("radio", { name: /Conversation/ }));
		// A conversation is settled or it is not, so its one occasion is chosen for the author rather
		// than left as an empty list that cannot be saved.
		await expect(canvas.getByRole("checkbox", { name: "Discussion settled" })).toBeChecked();
		await userEvent.type(
			canvas.getByRole("textbox", { name: /What to look for/ }),
			"Check whether the conversation stays constructive.",
		);
		await userEvent.click(canvas.getByRole("button", { name: "Create practice" }));
		await expect(createSubmit).toHaveBeenCalledWith(
			{
				name: "Helpful discussion",
				slug: "helpful-discussion",
				criteria: "Check whether the conversation stays constructive.",
				bindings: [
					{
						signals: ["chat.conversation_thread.settled"],
						needs: mockPracticeDefinitionOptions.workTypes[2].recommendedNeeds,
					},
				],
				automatedReviewPolicy: mockPracticeDefinitionOptions.workTypes[2].recommendedPolicy,
			},
			null,
		);
	},
};
