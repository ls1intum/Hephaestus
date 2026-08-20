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

const coverage = {
	preview: {
		data: {
			current: settings.coverageSummary,
			proposed: { ...settings.coverageSummary, coveredRepositories: 3 },
			widens: true,
		},
		isPending: false,
		isError: false,
		onPreview: fn(),
	},
	repositories: {
		options: ["acme/widgets", "acme/gadgets"].map((value) => ({ value, label: value })),
		isLoading: false,
		isError: false,
	},
	people: {
		options: [
			{ value: 7, label: "Ada Lovelace", description: "@ada" },
			{ value: 8, label: "Grace Hopper", description: "@grace" },
		],
		isLoading: false,
		isError: false,
	},
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
		coverage,
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
					repositoryMode: "SELECTED",
					personMode: "SELECTED",
					repositories: [
						{ nameWithOwner: "acme/widgets", baseBranches: ["main", "release/2026.1"] },
					],
					personUserIds: [7],
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
				reviewScope: {
					repositoryMode: "SELECTED",
					personMode: "ALL_ELIGIBLE",
					repositories: [{ nameWithOwner: "acme/widgets", baseBranches: [] }],
					personUserIds: [],
				},
			},
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.type(
			canvas.getByLabelText("Base branches for acme/widgets"),
			"  release/2026.1  ",
		);
		await userEvent.click(
			canvas.getByRole("button", { name: "Add to base branches for acme/widgets" }),
		);

		// Trimmed on the way out: a name with a stray space matches no branch the gate ever sees.
		await expect(args.policy.onUpdate).toHaveBeenCalledWith({
			reviewScope: {
				repositoryMode: "SELECTED",
				personMode: "ALL_ELIGIBLE",
				repositories: [{ nameWithOwner: "acme/widgets", baseBranches: ["release/2026.1"] }],
				personUserIds: [],
			},
		});
	},
};

/** Saying so has to reach the input itself: the Add button greying out announces nothing. */
export const RefusingADuplicate: Story = {
	args: {
		policy: {
			...policy,
			onUpdate: fn(),
			settings: {
				...settings,
				reviewScope: {
					repositoryMode: "SELECTED",
					personMode: "ALL_ELIGIBLE",
					repositories: [{ nameWithOwner: "acme/widgets", baseBranches: ["main"] }],
					personUserIds: [],
				},
			},
		},
	},
	play: async ({ args, canvas }) => {
		const input = canvas.getByLabelText("Base branches for acme/widgets");
		await userEvent.type(input, "main");

		await expect(input).toBeInvalid();
		await expect(input).toHaveAccessibleDescription(/main is already listed\./);
		await expect(
			canvas.getByRole("button", { name: "Add to base branches for acme/widgets" }),
		).toBeDisabled();

		await userEvent.type(input, "{Enter}");
		await expect(args.policy.onUpdate).not.toHaveBeenCalled();
	},
};

export const SelectedEmptyMeansNobody: Story = {
	args: {
		policy: {
			...policy,
			settings: {
				...settings,
				reviewScope: {
					repositoryMode: "SELECTED",
					personMode: "SELECTED",
					repositories: [],
					personUserIds: [],
				},
			},
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No repositories are covered.")).toBeVisible();
		await expect(canvas.getByText("No people are covered.")).toBeVisible();
	},
};

export const WideningRequiresConfirmation: Story = {
	args: {
		policy: {
			...policy,
			onUpdate: fn(),
			settings: {
				...settings,
				reviewScope: {
					repositoryMode: "SELECTED",
					personMode: "ALL_ELIGIBLE",
					repositories: [{ nameWithOwner: "acme/widgets", baseBranches: [] }],
					personUserIds: [],
				},
			},
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All monitored repositories" }));
		await expect(canvas.getByRole("alertdialog")).toBeVisible();
		await expect(canvas.getByText(/Monitored repositories covered:/)).toHaveTextContent("3");
		await expect(canvas.getByText(/Workspace-wide context:/)).toHaveTextContent(
			"not just this proposed population",
		);
		await expect(args.coverage.preview.onPreview).toHaveBeenCalled();
		await userEvent.click(canvas.getByRole("button", { name: "Widen coverage" }));
		await expect(args.policy.onUpdate).toHaveBeenCalled();
	},
};

export const CoveragePreviewLoading: Story = {
	args: {
		...WideningRequiresConfirmation.args,
		coverage: {
			...coverage,
			preview: { data: undefined, isPending: true, isError: false, onPreview: fn() },
		},
	},
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All monitored repositories" }));
		await expect(canvas.getByText("Calculating the proposed coverage…")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Widen coverage" })).toBeDisabled();
	},
};

export const CoveragePreviewUnavailable: Story = {
	args: {
		...WideningRequiresConfirmation.args,
		coverage: {
			...coverage,
			preview: { data: undefined, isPending: false, isError: true, onPreview: fn() },
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All monitored repositories" }));
		await expect(canvas.getByText("Couldn't preview this change")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(args.coverage.preview.onPreview).toHaveBeenCalledTimes(2);
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
