import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";
import type { AgentBinding } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeReviewSettings } from "./PracticeReviewSettings";
import { mockReviewSettings } from "./story-mock-data";

const settings = mockReviewSettings({ deliverToMerged: false });

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_REVIEW",
	enabled: true,
	ready: true,
	instanceModelId: 1,
};

const model = {
	binding: readyBinding,
	isLoading: false,
	isError: false,
	onRetry: fn(),
};

const workspace = {
	enabled: true,
	autoTriggerEnabled: true,
	manualTriggerEnabled: true,
	isSaving: false,
	onUpdate: fn(),
};

const policy = {
	settings,
	isSaving: false,
	onUpdate: fn(),
	onReset: fn(),
};

const meta = {
	title: "Workspace admin/Practices/Review/When and where",
	component: PracticeReviewSettings,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "acme",
		model,
		workspace,
		policy,
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-3xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeReviewSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A switch row stays a row: the control sits after its label and description, on the same line. This
 * is measured rather than believed because the reflow fix below it could have been bought by letting
 * the row stack instead, which is not the layout this surface wants at any width.
 */
async function expectSwitchSitsBesideItsLabel(control: HTMLElement) {
	const field = control.closest<HTMLElement>('[data-slot="field"]');
	const content = field?.querySelector<HTMLElement>('[data-slot="field-content"]');
	await expect(content, "The switch is not in a Field with a FieldContent.").not.toBeNull();

	const controlBox = control.getBoundingClientRect();
	const contentBox = (content as HTMLElement).getBoundingClientRect();
	await expect(controlBox.left).toBeGreaterThanOrEqual(contentBox.right);
	await expect(controlBox.top).toBeGreaterThanOrEqual(contentBox.top);
	await expect(controlBox.top).toBeLessThan(contentBox.bottom);
}

export const Configured: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ canvas }) => {
		await expectNoPageOverflow();
		// Fitting at 320px is not the same as fitting by stacking: the row survives the narrow width.
		await expectSwitchSitsBesideItsLabel(
			canvas.getByRole("switch", { name: "Start practice reviews" }),
		);
	},
};

export const NoModelSelected: Story = {
	args: { model: { ...model, binding: undefined }, workspace: { ...workspace, enabled: false } },
};

export const SelectedModelUnavailable: Story = {
	args: { model: { ...model, binding: { ...readyBinding, ready: false } } },
};

/**
 * Ready and turned off are two different facts about a model, and only one of them is `ready`. A
 * model that has been switched off elsewhere is still reported as ready by the binding, so a
 * readiness check that stops there offers to start reviews that cannot run.
 */
export const SelectedModelTurnedOff: Story = {
	args: {
		model: { ...model, binding: { ...readyBinding, enabled: false } },
		workspace: { ...workspace, enabled: false },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("The review model is unavailable")).toBeVisible();
		await expect(canvas.getByRole("switch", { name: "Start practice reviews" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

export const CheckingModelReadiness: Story = {
	args: { model: { ...model, binding: undefined, isLoading: true } },
};

export const ModelReadinessUnavailable: Story = {
	args: { model: { ...model, binding: undefined, isError: true } },
};

/**
 * Reviews on with both doors shut is the one state the page banner cannot see — it reads the switch
 * and the model, not the triggers — so it is said beside the switches that cause it.
 */
export const TriggersOff: Story = {
	args: {
		workspace: { ...workspace, autoTriggerEnabled: false, manualTriggerEnabled: false },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing can start a review")).toBeVisible();
		await expect(canvas.getByText(/both ways in are switched off/i)).toBeVisible();
	},
};

export const RequestedReviewsNamesEveryDoor: Story = {
	play: async ({ canvas }) => {
		const requested = canvas.getByRole("switch", { name: "Reviews somebody asks for" });
		await expect(requested).toBeVisible();

		// This story runs at the default viewport; the width is asserted so the row check below cannot
		// pass by being measured at a narrow one.
		await expect(window.innerWidth).toBeGreaterThanOrEqual(640);
		await expectSwitchSitsBesideItsLabel(requested);

		const description = canvas.getByText(/Turning this off stops every one of them/i);
		await expect(description).toHaveTextContent("Review this now");
		await expect(description).toHaveTextContent("backfill of past work");
		await expect(description).toHaveTextContent("recurring check");
		// Only GitLab publishes the comment event, so the copy must not promise it everywhere.
		await expect(description).toHaveTextContent("GitLab merge request comment");
	},
};

export const ReviewsPaused: Story = {
	args: { workspace: { ...workspace, enabled: false } },
};

export const ReviewScopeNarrowed: Story = {
	args: {
		policy: {
			...policy,
			settings: {
				...settings,
				reviewScope: {
					targetBranches: ["main", "release/2026.1"],
					repositories: ["acme/widgets", "acme/gadgets"],
				},
			},
		},
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};

/**
 * Adding one entry has to send the *whole* narrowed scope, not just the branch that was typed:
 * patching one list would silently drop the other and widen reviews to every repository.
 */
export const AddingATargetBranch: Story = {
	args: {
		policy: {
			...policy,
			onUpdate: fn(),
			settings: {
				...settings,
				reviewScope: { targetBranches: [], repositories: ["acme/widgets"] },
			},
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.type(canvas.getByLabelText("Target branches"), "  release/2026.1  ");
		await userEvent.click(canvas.getByRole("button", { name: "Add to target branches" }));

		// Trimmed on the way out: a name with a stray space matches no branch the gate ever sees.
		await expect(args.policy.onUpdate).toHaveBeenCalledWith({
			reviewScope: { targetBranches: ["release/2026.1"], repositories: ["acme/widgets"] },
		});
	},
};

export const EnterAddsTheEntry: Story = {
	args: { policy: { ...policy, onUpdate: fn() } },
	play: async ({ args, canvas }) => {
		await userEvent.type(canvas.getByLabelText("Repositories"), "acme/gadgets{Enter}");

		await expect(args.policy.onUpdate).toHaveBeenCalledWith({
			reviewScope: { targetBranches: [], repositories: ["acme/gadgets"] },
		});
	},
};

/** Saying so has to reach the input itself: the Add button greying out announces nothing. */
export const RefusingADuplicate: Story = {
	args: {
		policy: {
			...policy,
			onUpdate: fn(),
			settings: { ...settings, reviewScope: { targetBranches: ["main"], repositories: [] } },
		},
	},
	play: async ({ args, canvas }) => {
		const input = canvas.getByLabelText("Target branches");
		await userEvent.type(input, "main");

		await expect(input).toBeInvalid();
		await expect(input).toHaveAccessibleDescription(/main is already listed\./);
		await expect(canvas.getByRole("button", { name: "Add to target branches" })).toBeDisabled();

		await userEvent.type(input, "{Enter}");
		await expect(args.policy.onUpdate).not.toHaveBeenCalled();
	},
};

export const RemovingATargetBranch: Story = {
	args: {
		policy: {
			...policy,
			onUpdate: fn(),
			settings: {
				...settings,
				reviewScope: { targetBranches: ["main", "release/2026.1"], repositories: [] },
			},
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Remove release/2026.1" }));

		await expect(args.policy.onUpdate).toHaveBeenCalledWith({
			reviewScope: { targetBranches: ["main"], repositories: [] },
		});
	},
};

/** Widening back to everything is a reset rather than an empty save. */
export const WideningTheScopeAgain: Story = {
	args: {
		policy: {
			...policy,
			onReset: fn(),
			settings: {
				...settings,
				reviewScope: { targetBranches: ["main"], repositories: [] },
			},
		},
	},
	play: async ({ args, canvas }) => {
		const reset = canvas.getByRole("button", { name: /^Review everything again/ });
		await userEvent.click(reset);

		await expect(args.policy.onReset).toHaveBeenCalledWith("REVIEW_SCOPE");
	},
};

export const ChangingTheTimeBetweenReviews: Story = {
	args: { policy: { ...policy, onUpdate: fn() } },
	play: async ({ args, canvas }) => {
		const cooldown = canvas.getByRole("spinbutton", { name: "Time between reviews (minutes)" });

		// Passing through the field is not an edit.
		await userEvent.click(cooldown);
		await userEvent.tab();
		await expect(args.policy.onUpdate).not.toHaveBeenCalled();

		await userEvent.clear(cooldown);
		await userEvent.type(cooldown, "45");
		await userEvent.tab();

		await expect(args.policy.onUpdate).toHaveBeenCalledWith({ cooldownMinutes: 45 });
	},
};

/**
 * `aria-invalid` says only *that* something is wrong, so the field also points at the sentence that
 * says what would be right (WCAG 2.2 SC 3.3.1).
 */
export const RefusingATimeOutsideTheRange: Story = {
	args: { policy: { ...policy, onUpdate: fn() } },
	play: async ({ args, canvas }) => {
		const cooldown = canvas.getByRole("spinbutton", { name: "Time between reviews (minutes)" });
		await userEvent.clear(cooldown);
		await userEvent.type(cooldown, "1500");
		await userEvent.tab();

		await expect(cooldown).toBeInvalid();
		await expect(cooldown).toHaveAccessibleDescription("Enter a whole number from 0 to 1,440.");
		await expect(args.policy.onUpdate).not.toHaveBeenCalled();
	},
};
