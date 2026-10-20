import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { RouteError } from "./RouteError";

/**
 * What a route shows when its loader or component fails. Retry runs the route again; the route test
 * owns that a page that recovers replaces this one.
 */
const meta = {
	component: RouteError,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
} satisfies Meta<typeof RouteError>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		const alert = canvas.getByRole("alert");
		await expect(alert).toHaveTextContent("We could not load this page");
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

export const OnAPhone: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expect(canvasElement.scrollWidth).toBeLessThanOrEqual(canvasElement.clientWidth);
	},
};
