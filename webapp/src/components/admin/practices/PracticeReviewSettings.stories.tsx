import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";
import type {
	AgentBinding,
	PracticeReviewSettings as PracticeReviewSettingsData,
} from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeReviewSettings } from "./PracticeReviewSettings";

const settings: PracticeReviewSettingsData = {
	runForAllUsers: true,
	deliverToMerged: false,
	cooldownMinutes: 30,
	reviewScope: { targetBranches: [], repositories: [] },
	// Neither has been chosen here, so both show what a fresh workspace gets.
	defaultReviewTier: "DELIVER",
	feedbackReach: "ON_THE_WORK",
};

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

export const Configured: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		await expectNoPageOverflow();
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

export const ReviewRoleOnly: Story = {
	args: { policy: { ...policy, settings: { ...settings, runForAllUsers: false } } },
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};

/**
 * Both ways in switched off is the state where practice reviews are "on" and nothing can ever start
 * one. The status line has to say that outright, or the page reads as working.
 */
export const TriggersOff: Story = {
	args: {
		workspace: { ...workspace, autoTriggerEnabled: false, manualTriggerEnabled: false },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/both ways in are switched off/i)).toBeVisible();
	},
};

/**
 * One switch, four doors. Naming only `/hephaestus review` promised a GitHub workspace a command
 * only GitLab publishes, and hid that the same switch stops backfills and recurring checks.
 */
export const RequestedReviewsNamesEveryDoor: Story = {
	play: async ({ canvas }) => {
		const requested = canvas.getByRole("switch", { name: "Reviews somebody asks for" });
		await expect(requested).toBeVisible();

		const description = canvas.getByText(/Turning this off stops every one of them/i);
		await expect(description).toHaveTextContent("Review this now");
		await expect(description).toHaveTextContent("backfill of past work");
		await expect(description).toHaveTextContent("recurring check");
		// Scoped to the provider that publishes the comment event, not promised to every workspace.
		await expect(description).toHaveTextContent("GitLab merge request comment");
	},
};

export const ReviewsPaused: Story = {
	args: { workspace: { ...workspace, enabled: false } },
};

/** Empty means everything — a workspace that has never expressed an opinion must not review nothing. */
export const ReviewScopeUnrestricted: Story = {
	args: {
		policy: {
			...policy,
			settings: { ...settings, reviewScope: { targetBranches: [], repositories: [] } },
		},
	},
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

/** Enter is how a list like this is filled, so it must add the entry without using the button. */
export const EnterAddsTheEntry: Story = {
	args: { policy: { ...policy, onUpdate: fn() } },
	play: async ({ args, canvas }) => {
		await userEvent.type(canvas.getByLabelText("Repositories"), "acme/gadgets{Enter}");

		await expect(args.policy.onUpdate).toHaveBeenCalledWith({
			reviewScope: { targetBranches: [], repositories: ["acme/gadgets"] },
		});
	},
};

/**
 * A repeated entry cannot be added, and saying so has to reach the input itself: the Add button
 * greying out announces nothing.
 */
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

		// Enter is refused on the same terms rather than sending a duplicate the server would reject.
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

/**
 * Widening back to everything is a reset rather than an empty save, and the words on the control open
 * its accessible name so a voice-control user can activate what they can read (WCAG 2.2 SC 2.5.3).
 */
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

/**
 * The cooldown is saved on the way out of the field rather than on every keystroke, so leaving it
 * alone has to be silent and leaving it changed has to save.
 */
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
 * A number outside the range stays in the field. `aria-invalid` says only *that* something is wrong,
 * so the field also points at the sentence that says what would be right (WCAG 2.2 SC 3.3.1).
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
