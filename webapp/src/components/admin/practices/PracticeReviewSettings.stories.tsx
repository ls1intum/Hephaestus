import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import type {
	AgentBinding,
	PracticeReviewSettings as PracticeReviewSettingsData,
} from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeReviewSettings } from "./PracticeReviewSettings";

const settings: PracticeReviewSettingsData = {
	runForAllUsers: true,
	skipDrafts: true,
	deliverToMerged: false,
	cooldownMinutes: 30,
};

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
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
