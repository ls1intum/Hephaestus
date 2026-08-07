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
	title: "Workspace admin/Practices/Review settings",
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

export const TriggersOff: Story = {
	args: {
		workspace: { ...workspace, autoTriggerEnabled: false, manualTriggerEnabled: false },
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
 * The scope lists hold a draft of their own and are the only thing on this screen that does. Adding
 * one entry has to send the *whole* narrowed scope, not just the branch that was typed — sending a
 * patch of one list would silently drop the other and widen reviews to every repository.
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

/** Enter is how a list like this is filled; reaching for the mouse between entries is the slow path. */
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
 * A repeated entry cannot be added. Saying so has to reach the input itself: the only other sign is
 * the Add button quietly greying out, which announces nothing and explains less.
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

		// Enter is the other way in, and it is refused on the same terms rather than sending a
		// duplicate the server would have to reject.
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
