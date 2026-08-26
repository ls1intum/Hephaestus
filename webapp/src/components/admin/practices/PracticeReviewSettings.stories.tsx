import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { AgentBinding, PracticeReviewCoveragePreview } from "@/api/types.gen";
import { expectGenuinelyDisabled, expectUnavailable } from "@/test/controls";
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
	preview: fn(async () => ({
		current: settings.coverageSummary,
		proposed: { ...settings.coverageSummary, coveredRepositories: 3 },
		widens: true,
	})),
	repositories: {
		options: ["acme/widgets", "acme/gadgets"].map((value) => ({ value, label: value })),
		isLoading: false,
		isError: false,
		error: undefined,
		onRetry: fn(),
	},
	people: {
		options: [
			{ value: 7, label: "Ada Lovelace", description: "@ada" },
			{ value: 8, label: "Grace Hopper", description: "@grace" },
		],
		isLoading: false,
		isError: false,
		error: undefined,
		onRetry: fn(),
	},
};

function selectedRepositorySettings(baseBranches: string[]) {
	return {
		...settings,
		reviewScope: {
			repositoryMode: "SELECTED" as const,
			personMode: "ALL_ELIGIBLE" as const,
			repositories: [{ nameWithOwner: "acme/widgets", baseBranches }],
			personUserIds: [],
		},
	};
}

function narrowingCoverage() {
	return {
		...coverage,
		preview: fn(async () => ({
			current: settings.coverageSummary,
			proposed: settings.coverageSummary,
			widens: false,
		})),
	};
}

function pendingCoverage() {
	return {
		...coverage,
		preview: fn(() => new Promise<PracticeReviewCoveragePreview>(() => {})),
	};
}

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
	play: async ({ canvas }) => {
		const requestedReviews = canvas.getByText(/Turning this off stops every one of them/i);
		await expect(requestedReviews).toHaveTextContent("Review this now");
		await expect(requestedReviews).toHaveTextContent("backfill of past work");
		await expect(requestedReviews).toHaveTextContent("recurring check");
		await expect(requestedReviews).toHaveTextContent("GitLab merge request comment");
		await expectNoPageOverflow();
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
				error: undefined,
				onRetry: fn(),
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
				error: undefined,
				onRetry: fn(),
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
		await expect(canvas.getByText("Not monitored")).toBeVisible();
		await expectNoPageOverflow();
	},
};

/**
 * Adding one entry has to send the *whole* narrowed scope, not just the branch that was typed:
 * patching one list would silently drop the other and widen reviews to every repository.
 */
export const NarrowingToABaseBranch: Story = {
	args: {
		policy: {
			...policy,
			onUpdate: fn(),
			settings: selectedRepositorySettings([]),
		},
		coverage: narrowingCoverage(),
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

		await expect(args.policy.onUpdate).toHaveBeenCalledWith(
			{
				reviewScope: {
					repositoryMode: "SELECTED",
					personMode: "ALL_ELIGIBLE",
					repositories: [{ nameWithOwner: "acme/widgets", baseBranches: ["release/2026.1"] }],
					personUserIds: [],
				},
			},
			settings.etag,
		);
	},
};

export const RemovingABaseBranch: Story = {
	args: {
		policy: {
			...policy,
			settings: selectedRepositorySettings(["main", "release/2026.1"]),
		},
		coverage: pendingCoverage(),
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Base branches for acme/widgets" }));
		await userEvent.click(
			canvas.getByRole("button", {
				name: "Remove release/2026.1 from base branches for acme/widgets",
			}),
		);

		await expect(args.coverage.preview).toHaveBeenCalledWith({
			repositoryMode: "SELECTED",
			personMode: "ALL_ELIGIBLE",
			repositories: [{ nameWithOwner: "acme/widgets", baseBranches: ["main"] }],
			personUserIds: [],
		});
	},
};

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
		await expectGenuinelyDisabled(
			canvas.getByRole("button", { name: "Add to base branches for acme/widgets" }),
		);

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
		await expect(canvas.getByText("Nothing is being reviewed")).toBeVisible();
		await expect(canvas.getByText(/an empty list covers nobody/i)).toBeVisible();
	},
};

export const SendingPaused: Story = {
	args: {
		policy: { ...policy, settings: { ...settings, deliveryStatus: "PAUSED" } },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Sending is paused")).toBeVisible();
		await expect(canvas.getByRole("switch", { name: /Send feedback/ })).not.toBeChecked();
		await expect(
			canvas.getByText(/proposals waiting for approval stay in your queue/),
		).toBeVisible();
	},
};

export const WideningCoverageAsksFirst: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
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
			preview: fn(async () => ({
				current: { ...settings.coverageSummary, coveredRepositories: 1 },
				proposed: settings.coverageSummary,
				widens: true,
			})),
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All monitored repositories" }));

		const dialog = within(await screen.findByRole("alertdialog"));
		await expectNoPageOverflow();
		await expect(dialog.getByText(/Monitored repositories covered:/)).toHaveTextContent(
			"Monitored repositories covered: 1 → 3 of 3",
		);
		await expect(dialog.getByText(/Workspace-wide context:/)).toHaveTextContent(
			"not just this proposed population",
		);
		await expect(args.coverage.preview).toHaveBeenCalled();

		await userEvent.click(dialog.getByRole("button", { name: "Apply wider coverage" }));
		await expect(args.policy.onUpdate).toHaveBeenCalledWith(
			{
				reviewScope: {
					repositoryMode: "ALL_MONITORED",
					personMode: "ALL_ELIGIBLE",
					repositories: [{ nameWithOwner: "acme/widgets", baseBranches: [] }],
					personUserIds: [],
				},
			},
			settings.etag,
		);
	},
};

export const CoveragePreviewLoading: Story = {
	args: {
		...WideningCoverageAsksFirst.args,
		coverage: {
			...coverage,
			preview: fn(
				() =>
					new Promise<PracticeReviewCoveragePreview>(() => {
						// Deliberately pending to keep the loading state visible.
					}),
			),
		},
	},
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All monitored repositories" }));

		await expect(canvas.getByRole("radio", { name: "All monitored repositories" })).toBeChecked();
		await expect(canvas.getByRole("status")).toHaveTextContent("Checking the proposed coverage…");
		await expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
	},
};

export const CoveragePreviewUnavailable: Story = {
	args: {
		...WideningCoverageAsksFirst.args,
		coverage: {
			...coverage,
			preview: fn(async () => {
				throw new Error("preview unavailable");
			}),
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All monitored repositories" }));

		await expectSettledVisible(canvas.getByText("Couldn't estimate the new coverage"));
		await expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(args.coverage.preview).toHaveBeenCalledTimes(2);
	},
};

export const ChangingTheTimeBetweenReviews: Story = {
	args: { policy: { ...policy, onUpdate: fn() } },
	play: async ({ args, canvas }) => {
		const cooldown = canvas.getByRole("spinbutton", { name: "Time between reviews (minutes)" });

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
