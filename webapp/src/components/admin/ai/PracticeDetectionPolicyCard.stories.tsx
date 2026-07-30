import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import type { AgentBinding } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeDetectionPolicyCard } from "./PracticeDetectionPolicyCard";
import { mockAvailableModels, mockPracticeReviewSettings } from "./story-mock-data";

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	enabled: true,
	ready: true,
	instanceModelId: 1,
};

const meta = {
	title: "Admin/Practices/Review settings",
	component: PracticeDetectionPolicyCard,
	parameters: {
		a11y: { test: "error" },
		layout: "padded",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	args: {
		settings: mockPracticeReviewSettings,
		detectionBinding: readyBinding,
		availableModels: mockAvailableModels,
		workspaceSlug: "acme",
		autoTriggerEnabled: true,
		manualTriggerEnabled: true,
		isLoading: false,
		savingReviewSettings: false,
		savingTriggers: false,
		onUpdateReviewSettings: fn(),
		onUpdateTriggers: fn(),
		onResetReviewField: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-3xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeDetectionPolicyCard>;

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
	args: { detectionBinding: undefined },
};

export const SelectedModelUnavailable: Story = {
	args: { detectionBinding: { ...readyBinding, ready: false } },
};

export const ReviewRoleOnly: Story = {
	args: {
		settings: { ...mockPracticeReviewSettings, runForAllUsers: false },
	},
};

export const WorkspaceDefaults: Story = {
	args: {
		settings: {
			...mockPracticeReviewSettings,
			skipDraftsOverride: undefined,
			deliverToMergedOverride: undefined,
			cooldownMinutesOverride: undefined,
			runForAllUsersOverride: undefined,
		},
	},
};

export const TriggersOff: Story = {
	args: { autoTriggerEnabled: false, manualTriggerEnabled: false },
};

export const SavingReviewPolicy: Story = {
	args: { savingReviewSettings: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("switch", { name: "Skip drafts" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(canvas.getByRole("switch", { name: "Automatic reviews" })).not.toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

export const Loading: Story = {
	args: { isLoading: true, settings: undefined },
};

export const PermissionDenied: Story = {
	args: {
		isError: true,
		settings: undefined,
		error: { status: 403, detail: "You are not an admin of this workspace." },
		onRetry: fn(),
	},
};

export const EditPolicy: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("switch", { name: "Automatic reviews" }));
		await expect(args.onUpdateTriggers).toHaveBeenCalledWith({
			practiceReviewAutoTriggerEnabled: false,
		});

		const interval = canvas.getByRole("spinbutton", { name: "Time between reviews (minutes)" });
		await userEvent.clear(interval);
		await userEvent.type(interval, "45");
		await userEvent.tab();
		await expect(args.onUpdateReviewSettings).toHaveBeenCalledWith({ cooldownMinutes: 45 });
	},
};
