import type { Meta, StoryObj } from "@storybook/react-vite";
import { delay, http } from "msw";
import { expect, userEvent, within } from "storybook/test";
import { MyPracticesPage } from "@/components/practices/MyPracticesPage";
import {
	emptyMyReportHandler,
	myReportErrorOnceHandler,
	myReportHandler,
	sparseMyReportHandler,
} from "@/components/practices/story-data";

/**
 * The developer self view container: fetches the caller's own report through the generated
 * query options (mocked here with MSW) and renders the focus queue. The transparency line
 * states plainly who else can see this data.
 */
const meta = {
	component: MyPracticesPage,
	parameters: {
		layout: "fullscreen",
		msw: { handlers: [myReportHandler] },
	},
	tags: ["autodocs"],
	args: { workspaceSlug: "nimbus" },
} satisfies Meta<typeof MyPracticesPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A filled report with the transparency line under the title. */
export const Filled: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Include tests with the change")).toBeInTheDocument();
		await expect(
			canvas.getByText(
				"Workspace admins can see your practice status to support mentoring, and every detailed view is recorded.",
			),
		).toBeInTheDocument();
	},
};

/** A first-cycle report. */
export const SparseNewWorkspace: Story = {
	parameters: { msw: { handlers: [sparseMyReportHandler] } },
};

/** No activity yet. */
export const Empty: Story = {
	parameters: { msw: { handlers: [emptyMyReportHandler] } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Your first focus arrives soon")).toBeInTheDocument();
	},
};

/** The report request keeps loading. */
export const Loading: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get("*/workspaces/:slug/practices/reports/me", async () => {
					await delay("infinite");
				}),
			],
		},
	},
};

/** The report request fails once; retrying loads the queue. */
export const ErrorWithRetry: Story = {
	parameters: { msw: { handlers: [myReportErrorOnceHandler, myReportHandler] } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByText("Your practice report could not be loaded right now."),
		).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Try again" }));
		await expect(
			await canvas.findByText("Include tests with the change", undefined, { timeout: 5000 }),
		).toBeInTheDocument();
	},
};
