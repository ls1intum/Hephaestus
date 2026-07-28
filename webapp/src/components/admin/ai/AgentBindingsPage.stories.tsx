import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, type within } from "storybook/test";
import type { AgentBinding } from "@/api/types.gen";
import { withStandardPage } from "@/stories/decorators";
import { expectControlOnScreen, expectNoPageOverflow } from "@/test/reflow";
import { AgentBindingsPage } from "./AgentBindingsPage";
import { mockAvailableModels } from "./story-mock-data";

type Canvas = ReturnType<typeof within>;

async function openAdvancedOnFirstCard(canvas: Canvas) {
	await userEvent.click((await canvas.findAllByRole("button", { name: /Advanced/ }))[0]);
}

const detectionBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	instanceModelId: 1,
	enabled: true,
	ready: true,
	timeoutSeconds: 600,
	maxConcurrentJobs: 3,
	allowInternet: false,
};

const meta = {
	component: AgentBindingsPage,
	parameters: { layout: "fullscreen" },
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "acme",
		bindings: [detectionBinding],
		availableModels: mockAvailableModels,
		practicesEnabled: true,
		mentorEnabled: true,
		isLoading: false,
		isError: false,
		loadError: null,
		pendingPurposes: new Set(),
		onRetry: fn(),
		onSave: fn(),
		onTurnOff: fn(),
	},
} satisfies Meta<typeof AgentBindingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const DetectionBoundMentorUnbound: Story = {};

export const Loading: Story = {
	args: { isLoading: true },
};

export const NoModelsAvailable: Story = {
	args: { bindings: [], availableModels: [] },
	play: async ({ canvas }) => {
		await canvas.findAllByText(/No models are available yet/);
	},
};

export const LoadForbidden: Story = {
	args: {
		isError: true,
		loadError: {
			type: "about:blank",
			title: "Forbidden",
			status: 403,
			detail: "You are not an admin of this workspace.",
			instance: "/workspaces/acme/agents",
		},
	},
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("Couldn't load AI models")).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: "Retry" })).toBeNull();
	},
};

export const PurposeDisabledForWorkspace: Story = {
	args: { mentorEnabled: false },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByText("Turned off for this workspace. Only your host can turn it on."),
		).toBeInTheDocument();
	},
};

export const OnlyThePendingCardIsFrozen: Story = {
	args: { pendingPurposes: new Set(["PRACTICE_DETECTION" as const]) },
	play: async ({ canvas }) => {
		const [detectionSave, mentorSave] = await canvas.findAllByRole("button", { name: "Save" });
		await expect(detectionSave).toBeDisabled();
		await expect(mentorSave).toBeEnabled();
	},
};

export const AdvancedDisclosure: Story = {
	play: async ({ canvas }) => {
		await openAdvancedOnFirstCard(canvas);
		await expect(await canvas.findByLabelText("Timeout (seconds)")).toBeInTheDocument();
	},
};

export const InvalidRunLimit: Story = {
	play: async ({ canvas }) => {
		await openAdvancedOnFirstCard(canvas);

		await userEvent.clear(await canvas.findByLabelText("Timeout (seconds)"));
		await userEvent.click(canvas.getAllByRole("button", { name: "Save" })[0]);

		await expect(await canvas.findByText("Enter a number of seconds.")).toBeInTheDocument();
		await expect(screen.queryByRole("status")).toBeNull();
	},
};

export const MobileReflow: Story = {
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Practice feedback");
		await expectNoPageOverflow();
		await expectControlOnScreen(canvas.getAllByRole("button", { name: "Save" })[0]);
	},
};
