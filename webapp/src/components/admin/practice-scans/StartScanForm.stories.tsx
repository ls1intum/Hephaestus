import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";

import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { withPageBehind } from "@/stories/decorators";
import { settledDrawerPanel } from "@/stories/overlay";
import { Stateful } from "@/stories/stateful";
import { expectGenuinelyDisabled } from "@/test/controls";

import { ELIGIBLE_PRACTICES, REPOSITORIES } from "./fixtures";
import { GUARDED_SCAN_LEVEL_KINDS, startScanLevel } from "./scans-search";
import { StartScanForm } from "./StartScanForm";

const meta = {
	component: StartScanForm,
	parameters: { layout: "fullscreen", chromatic: { viewports: [1440] } },
	decorators: [withPageBehind],
	args: {
		workspaceSlug: "aet",
		options: { status: "ready", repositories: REPOSITORIES, practices: ELIGIBLE_PRACTICES },
		pending: false,
		cancel: <LevelCancel />,
		onSubmit: fn(),
	},
	argTypes: { cancel: { control: false }, options: { control: false } },
	render: (args) => (
		<Stateful initial={[startScanLevel()]}>
			{(stack, setStack) => (
				<DetailDrawerStack
					stack={stack}
					guardedKinds={GUARDED_SCAN_LEVEL_KINDS}
					onClose={(depth) => setStack(stack.slice(0, depth))}
				>
					{(_entry, level) => <StartScanForm {...args} nested={level.nested} />}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof StartScanForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ args }) => {
		await settledDrawerPanel();
		await expect(await screen.findByRole("combobox", { name: "Repository" })).toHaveTextContent(
			"ls1intum/hephaestus",
		);
		await expect(screen.getByRole("textbox", { name: "Branch" })).toHaveAttribute("readonly");
		await expect(screen.getByText(/3 of 3 selected/u)).toBeVisible();
		await expect(screen.getByRole("checkbox", { name: "Reuse unchanged results" })).toBeChecked();
		await userEvent.click(screen.getByRole("button", { name: "Start scan" }));
		await expect(args.onSubmit).toHaveBeenCalledWith({
			repositoryId: "300",
			practiceSlugs: ["error-handling", "comment-quality", "test-coverage"],
			budgetUsd: "10",
			reuse: true,
		});
	},
};

export const EmptySelection: Story = {
	play: async ({ args }) => {
		await settledDrawerPanel();
		for (const practice of ELIGIBLE_PRACTICES) {
			await userEvent.click(await screen.findByRole("checkbox", { name: practice.name }));
		}
		await expect(screen.getByText(/0 of 3 selected/u)).toBeVisible();
		await userEvent.click(screen.getByRole("button", { name: "Start scan" }));
		const error = await screen.findByText("Select at least one practice.");
		await expect(error).toBeVisible();
		const [first] = screen.getAllByRole("checkbox", { name: ELIGIBLE_PRACTICES[0]?.name });
		await expect(first).toHaveAccessibleDescription("Select at least one practice.");
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

export const InvalidBudget: Story = {
	play: async ({ args }) => {
		await settledDrawerPanel();
		const budget = await screen.findByRole("textbox", { name: "Budget (US dollars)" });
		await userEvent.clear(budget);
		await userEvent.type(budget, "0.00001");
		await userEvent.click(screen.getByRole("button", { name: "Start scan" }));
		await expect(
			await screen.findByText("Enter an amount in US dollars, with at most four decimal places."),
		).toBeVisible();
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

export const Starting: Story = {
	args: { pending: true },
	play: async () => {
		await settledDrawerPanel();
		const submit = await screen.findByRole("button", { name: "Starting scan…" });
		await expectGenuinelyDisabled(submit);
	},
};

export const AnotherScanRunning: Story = {
	args: { refusal: { kind: "active-scan", scanId: "a0000000-0000-4000-8000-000000000002" } },
	play: async () => {
		await settledDrawerPanel();
		await expect(
			await screen.findByRole("link", { name: "Open the running scan" }),
		).toHaveAttribute("href", "/w/aet/admin/practices/scans/a0000000-0000-4000-8000-000000000002");
	},
};

export const VersionChanged: Story = {
	args: {
		refusal: {
			kind: "version-changed",
			currentVersion: "9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d",
		},
		expectedVersion: "9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d",
	},
	play: async () => {
		await settledDrawerPanel();
		await expect(await screen.findByText("The repository changed")).toBeVisible();
		await expect(screen.getByText("The next start expects version 9e8d7c6.")).toBeVisible();
	},
};

export const ModelNotReady: Story = {
	args: {
		refusal: {
			kind: "refused",
			message: "Assign a ready AI model to practice reviews before you start a scan.",
		},
	},
	play: async () => {
		await settledDrawerPanel();
		await expect(await screen.findByText("The scan did not start")).toBeVisible();
	},
};

export const NoAnswer: Story = {
	args: { refusal: { kind: "failed", message: "The server did not answer." } },
	play: async () => {
		await settledDrawerPanel();
		await expect(await screen.findByText("We could not start the scan")).toBeVisible();
		await expect(screen.getByText(/Starting again cannot start a second scan\./u)).toBeVisible();
	},
};

export const NoEligiblePractices: Story = {
	args: { options: { status: "ready", repositories: REPOSITORIES, practices: [] } },
	play: async () => {
		await settledDrawerPanel();
		await expect(await screen.findByRole("link", { name: "Open Practice setup" })).toHaveAttribute(
			"href",
			"/w/aet/admin/practices",
		);
		await expect(screen.queryByRole("button", { name: "Start scan" })).not.toBeInTheDocument();
	},
};

export const NoRepositories: Story = {
	args: { options: { status: "ready", repositories: [], practices: ELIGIBLE_PRACTICES } },
	play: async () => {
		await settledDrawerPanel();
		await expect(await screen.findByText("No repository can be scanned")).toBeVisible();
	},
};

export const Loading: Story = {
	args: { options: { status: "loading" } },
	play: async () => {
		await settledDrawerPanel();
		await expect(await screen.findByRole("heading", { name: "Start scan" })).toBeVisible();
		await expect(screen.queryByRole("button", { name: "Start scan" })).not.toBeInTheDocument();
	},
};

export const Error: Story = {
	args: {
		options: { status: "error", error: { status: 500 }, onRetry: fn() },
	},
	play: async () => {
		await settledDrawerPanel();
		await expect(await screen.findByText("We could not load what a scan needs")).toBeVisible();
		await expect(screen.getByRole("button", { name: "Retry" })).toBeVisible();
	},
};
