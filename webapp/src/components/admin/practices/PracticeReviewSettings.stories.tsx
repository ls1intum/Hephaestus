import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { AgentBinding } from "@/api/types.gen";
import { expectUnavailable } from "@/test/controls";
import { expectSettledVisible } from "@/test/overlay";
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

/**
 * A switch row stays a row: the control sits after its label and description, on the same line. This
 * is measured rather than believed because the reflow fix below it could have been bought by letting
 * the row stack instead, which is not the layout this surface wants at any width.
 */
async function expectSwitchSitsBesideItsLabel(control: HTMLElement) {
	const field = control.closest<HTMLElement>('[data-slot="field"]');
	const content = field?.querySelector<HTMLElement>('[data-slot="field-content"]');
	if (!content) throw new Error("The switch is not in a Field with a FieldContent.");

	const controlBox = control.getBoundingClientRect();
	const contentBox = content.getBoundingClientRect();
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
		await expectUnavailable(canvas.getByRole("switch", { name: "Start practice reviews" }));
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

export const PilotPopulation: Story = {
	args: {
		policy: {
			...policy,
			settings: {
				...settings,
				coverageSummary: {
					...settings.coverageSummary,
					monitoredRepositories: 12,
					eligiblePeople: 24,
				},
				reviewScope: {
					repositoryMode: "SELECTED",
					personMode: "SELECTED",
					repositories: [
						{ nameWithOwner: "acme/widgets", baseBranches: ["main"] },
						{ nameWithOwner: "acme/gadgets", baseBranches: [] },
						{ nameWithOwner: "acme/sprockets", baseBranches: ["main", "release/2026.1"] },
						{ nameWithOwner: "acme/retired-service", baseBranches: [] },
					],
					personUserIds: [7, 8, 9, 10, 11],
				},
			},
		},
		coverage: {
			...coverage,
			repositories: {
				options: ["acme/widgets", "acme/gadgets", "acme/sprockets"].map((value) => ({
					value,
					label: value,
				})),
				isLoading: false,
				isError: false,
			},
			people: {
				options: [
					{ value: 7, label: "Ada Lovelace", description: "@ada" },
					{ value: 8, label: "Grace Hopper", description: "@grace" },
					{ value: 9, label: "Katherine Johnson", description: "@katherine" },
					{ value: 10, label: "Barbara Liskov", description: "@barbara" },
					{ value: 11, label: "Margaret Hamilton", description: "@margaret" },
				],
				isLoading: false,
				isError: false,
			},
		},
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("3 of 12 monitored")).toBeVisible();
		await expect(canvas.getByText("5 of 24 members")).toBeVisible();
		// A repository the workspace stopped syncing stays named in the scope and covers nothing.
		await expect(canvas.getByText("Not monitored")).toBeVisible();
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
		await userEvent.click(canvas.getByRole("button", { name: "Base branches for acme/widgets" }));
		await userEvent.type(
			canvas.getByLabelText("Only these base branches for acme/widgets"),
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

/** A repository already limited to one branch admits more work when a second is named. */
export const AddingASecondBranchWidens: Story = {
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
		await userEvent.click(canvas.getByRole("button", { name: "Base branches for acme/widgets" }));
		await userEvent.type(canvas.getByLabelText("Only these base branches for acme/widgets"), "dev");
		await userEvent.click(
			canvas.getByRole("button", { name: "Add to base branches for acme/widgets" }),
		);

		await screen.findByRole("alertdialog");
		await expect(args.policy.onUpdate).not.toHaveBeenCalled();
	},
};

export const RemovingOneOfTwoBranchesNarrows: Story = {
	args: {
		policy: {
			...policy,
			onUpdate: fn(),
			settings: {
				...settings,
				reviewScope: {
					repositoryMode: "SELECTED",
					personMode: "ALL_ELIGIBLE",
					repositories: [{ nameWithOwner: "acme/widgets", baseBranches: ["main", "dev"] }],
					personUserIds: [],
				},
			},
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Base branches for acme/widgets" }));
		await userEvent.click(
			canvas.getByRole("button", { name: "Remove dev from base branches for acme/widgets" }),
		);

		await expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
		await expect(args.policy.onUpdate).toHaveBeenCalledWith({
			reviewScope: {
				repositoryMode: "SELECTED",
				personMode: "ALL_ELIGIBLE",
				repositories: [{ nameWithOwner: "acme/widgets", baseBranches: ["main"] }],
				personUserIds: [],
			},
		});
	},
};

/** Naming no branch means every branch, so emptying the list is the widest this axis goes. */
export const ClearingEveryBranchWidens: Story = {
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
		await userEvent.click(canvas.getByRole("button", { name: "Base branches for acme/widgets" }));
		await userEvent.click(
			canvas.getByRole("button", { name: "Remove main from base branches for acme/widgets" }),
		);

		await screen.findByRole("alertdialog");
		await expect(args.policy.onUpdate).not.toHaveBeenCalled();
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
		await userEvent.click(canvas.getByRole("button", { name: "Base branches for acme/widgets" }));
		const input = canvas.getByLabelText("Only these base branches for acme/widgets");
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
		await expect(canvas.getByText("Nothing is being reviewed")).toBeVisible();
	},
};

/** Feedback composed while paused is dropped, not queued — the banner has to say so. */
export const SendingPaused: Story = {
	args: {
		policy: { ...policy, settings: { ...settings, deliveryStatus: "PAUSED" } },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Sending is paused")).toBeVisible();
		await expect(canvas.getByRole("switch", { name: /Send feedback/ })).not.toBeChecked();
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
		coverage: {
			...coverage,
			preview: {
				...coverage.preview,
				data: {
					current: { ...settings.coverageSummary, coveredRepositories: 1 },
					proposed: settings.coverageSummary,
					widens: true,
				},
			},
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All monitored repositories" }));

		const dialog = within(await screen.findByRole("alertdialog"));
		await expect(dialog.getByText(/Monitored repositories covered:/)).toHaveTextContent(
			"Monitored repositories covered: 1 → 3 of 3",
		);
		await expect(dialog.getByText(/Workspace-wide context:/)).toHaveTextContent(
			"not just this proposed population",
		);
		await expect(args.coverage.preview.onPreview).toHaveBeenCalled();

		await userEvent.click(dialog.getByRole("button", { name: "Widen coverage" }));
		await expect(args.policy.onUpdate).toHaveBeenCalledWith({
			reviewScope: {
				repositoryMode: "ALL_MONITORED",
				personMode: "ALL_ELIGIBLE",
				repositories: [{ nameWithOwner: "acme/widgets", baseBranches: [] }],
				personUserIds: [],
			},
		});
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

		const dialog = within(await screen.findByRole("alertdialog"));
		await expectSettledVisible(dialog.getByText("Calculating the proposed coverage…"));
		await expect(dialog.getByRole("button", { name: "Widen coverage" })).toBeDisabled();
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

		const dialog = within(await screen.findByRole("alertdialog"));
		await expectSettledVisible(dialog.getByText("Couldn't preview this change"));
		await expect(dialog.getByRole("button", { name: "Widen coverage" })).toBeDisabled();

		await userEvent.click(dialog.getByRole("button", { name: "Retry" }));
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
