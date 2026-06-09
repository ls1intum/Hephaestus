import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { ConsentBanner, type ConsentCategory } from "./ConsentBanner";

const analytics: ConsentCategory = {
	key: "analytics",
	name: "Usage analytics",
	description: "Helps us see how the app is used so we can improve it.",
};
const errorReports: ConsentCategory = {
	key: "errorMonitoring",
	name: "Error reports",
	description: "Tell us when something breaks so we can fix it.",
};

/**
 * Presentational cookie-consent banner. These stories drive the pure component directly (no store),
 * so every shape is reachable: two configured categories (granular toggles), a single category
 * (simple Allow/Decline), and the re-open edit mode.
 */
const meta = {
	component: ConsentBanner,
	parameters: { layout: "fullscreen" },
	args: {
		values: { analytics: false, errorMonitoring: false },
		editing: false,
		onToggle: () => {},
		onAcceptAll: () => {},
		onRejectAll: () => {},
		onSave: () => {},
		onCancel: () => {},
		privacyPolicy: (
			<a href="/privacy" className="underline underline-offset-4 hover:text-foreground">
				Read our Privacy Policy
			</a>
		),
	},
} satisfies Meta<typeof ConsentBanner>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Both integrations configured: granular toggles + equal-prominence Reject all / Accept all. */
export const BothCategories: Story = {
	args: { categories: [analytics, errorReports] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("region", { name: /your privacy/i })).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Reject all" })).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Accept all" })).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Save choices" })).toBeInTheDocument();
	},
};

/** Only analytics configured: no granular toggles, just a clear Allow / Decline pair. */
export const AnalyticsOnly: Story = {
	args: { categories: [analytics] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: "Allow" })).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Decline" })).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: "Save choices" })).not.toBeInTheDocument();
	},
};

/** Only error reporting configured. */
export const ErrorReportsOnly: Story = {
	args: { categories: [errorReports] },
};

/** Re-opened to change an existing decision: toggles are pre-seeded and a Cancel action appears. */
export const Editing: Story = {
	args: {
		categories: [analytics, errorReports],
		editing: true,
		values: { analytics: true, errorMonitoring: false },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
	},
};
