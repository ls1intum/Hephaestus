import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, waitFor } from "storybook/test";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import { LevelCancel } from "@/components/core/detail-drawer/LevelCancel";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { expectNoPanelOverflow } from "@/test/reflow";
import { PracticeForm } from "./PracticeForm";
import { PracticeFormLevel } from "./PracticeFormLevel";
import { GUARDED_LEVEL_KINDS, practiceFormLevel } from "./practice-search";
import { mockAreas, mockPracticeWithAllTriggers } from "./story-mock-data";

const createSubmit = fn();
const editSubmit = fn();

/**
 * The editor is a level of the practice-setup drawer stack, so the tree a practice belongs to stays
 * on screen while it is written — and so these stories exercise the surface people actually get.
 * The level is guarded: only Cancel and Save leave it.
 */
const meta = {
	title: "Workspace admin/Practices/Practice editor",
	component: PracticeForm,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	decorators: [withPageBehind],
	args: {
		mode: "create",
		workspaceSlug: "demo",
		areas: mockAreas,
		definitionOptions: mockPracticeDefinitionOptions,
		onSubmit: createSubmit,
		isPending: false,
		cancel: <LevelCancel />,
	},
	argTypes: { cancel: { control: false } },
	render: (args) => (
		<Stateful
			initial={[practiceFormLevel(args.mode === "edit" ? args.initialData.slug : undefined)]}
		>
			{(stack, setStack) => (
				<DetailDrawerStack
					stack={stack}
					guardedKinds={GUARDED_LEVEL_KINDS}
					onClose={(depth) => setStack(stack.slice(0, depth))}
				>
					{(entry, level) => (
						<PracticeFormLevel nested={level.nested} creating={entry.kind === "practice-new"}>
							<PracticeForm {...args} />
						</PracticeFormLevel>
					)}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeForm>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The editor is a drawer level, so it arrives over a transition rather than being simply present. */
async function settledEditor(): Promise<HTMLElement> {
	const [popup] = document.querySelectorAll<HTMLElement>('[data-slot="drawer-popup"]');
	await expectSettledVisible(popup);
	return popup;
}

export const Create: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		// The level is the full viewport at 320px, so the form has to fit it: 43 controls that scroll
		// down, never across.
		await expectNoPanelOverflow(await settledEditor());
	},
};

export const LeavingTheEditor: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async () => {
		const popup = await settledEditor();

		// A draft is not discarded by a stray gesture. `DetailDrawerStack` proves the mechanism; this
		// pins that the editor is one of the levels wired to it.
		await userEvent.keyboard("{Escape}");
		await expect(popup).not.toHaveAttribute("data-ending-style");

		await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
		await waitFor(() =>
			expect(document.querySelectorAll('[data-slot="drawer-popup"]')).toHaveLength(0),
		);
	},
};

export const EditWithAdvanced: Story = {
	args: { mode: "edit", initialData: mockPracticeWithAllTriggers, onSubmit: fn() },
};

export const Submitting: Story = {
	args: { isPending: true, onSubmit: fn() },
	play: async () => {
		await settledEditor();
		await expect(screen.getByRole("textbox", { name: /Name/ })).toBeDisabled();
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
	play: async () => {
		await settledEditor();
		editSubmit.mockClear();
		await userEvent.clear(screen.getByRole("textbox", { name: "Why it matters" }));
		await userEvent.clear(screen.getByRole("textbox", { name: "What good looks like" }));
		await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

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
	play: async () => {
		await settledEditor();
		await userEvent.click(screen.getByRole("button", { name: "Create practice" }));
		await expect(screen.getByText("Name must be at least 3 characters")).toBeVisible();
		await expect(screen.queryByText("Select at least one trigger event")).not.toBeInTheDocument();
		await expect(screen.getByRole("textbox", { name: /Name/ })).toHaveAttribute(
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

		await userEvent.type(screen.getByRole("textbox", { name: /Name/ }), "Clear review context");
		await userEvent.type(
			screen.getByRole("textbox", { name: /What to look for/ }),
			"Check whether the reviewed work explains its purpose.",
		);
		await userEvent.click(screen.getByRole("button", { name: "Create practice" }));
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
	play: async () => {
		await settledEditor();
		createSubmit.mockClear();
		await userEvent.type(screen.getByRole("textbox", { name: /Name/ }), "Helpful discussion");
		await userEvent.click(screen.getByRole("radio", { name: /Conversation/ }));
		// A conversation is settled or it is not, so its one occasion is chosen for the author rather
		// than left as an empty list that cannot be saved.
		await expect(screen.getByRole("checkbox", { name: "Discussion settled" })).toBeChecked();
		await userEvent.type(
			screen.getByRole("textbox", { name: /What to look for/ }),
			"Check whether the conversation stays constructive.",
		);
		await userEvent.click(screen.getByRole("button", { name: "Create practice" }));
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
