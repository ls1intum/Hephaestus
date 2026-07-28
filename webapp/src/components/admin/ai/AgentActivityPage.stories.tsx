import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectPageReflows, expectTargetSize } from "@/test/reflow";
import { AgentActivityPage } from "./AgentActivityPage";
import { mockJobs } from "./story-mock-data";

const PAGES_ENOUGH_TO_WINDOW_THE_PAGER = 12;

function handlers(totalPages = PAGES_ENOUGH_TO_WINDOW_THE_PAGER) {
	return [
		http.get("*/workspaces/acme/agents/jobs", ({ request }) => {
			const page = Number(new URL(request.url).searchParams.get("page") ?? 0);
			return HttpResponse.json({
				content: mockJobs,
				number: page,
				size: 20,
				totalPages,
				totalElements: totalPages * 20,
			});
		}),
	];
}

/** Every AI run in the workspace: a status filter, the runs table, and a windowed pager. */
const meta = {
	component: AgentActivityPage,
	parameters: { layout: "fullscreen", msw: { handlers: handlers() } },
	tags: ["autodocs"],
	args: { workspaceSlug: "acme" },
} satisfies Meta<typeof AgentActivityPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const SinglePage: Story = {
	parameters: { msw: { handlers: handlers(1) } },
	play: async ({ canvas }) => {
		await canvas.findByRole("table");
		await expect(canvas.queryByRole("navigation", { name: "pagination" })).toBeNull();
	},
};

/** WCAG 2.2 SC 1.4.10 and 2.5.8 at 320 px, for the pager the table's own story does not carry. */
export const MobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("table");

		await expectPageReflows();

		const pager = canvas.getByRole("navigation", { name: "pagination" });
		const targets = within(pager).getAllByRole("button");
		await expect(targets.length).toBeGreaterThan(2);
		for (const target of targets) {
			await expect(target.getBoundingClientRect().right).toBeLessThanOrEqual(window.innerWidth + 1);
			await expectTargetSize(target);
		}
	},
};

/**
 * WCAG 2.2 SC 1.4.4 at 200 %: text-only zoom grows the `rem`-sized pager targets without giving the
 * page more room, which is the case `flex-wrap` on the pager exists for. Simulating it means setting
 * the root font size — a property of the runner's whole document, so this story stands alone and
 * restores it in `finally`.
 */
export const PagerAtDoubleTextSize: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("table");
		const root = document.documentElement;
		const originalFontSize = root.style.fontSize;
		try {
			root.style.fontSize = "32px";
			await expectPageReflows();
		} finally {
			root.style.fontSize = originalFontSize;
		}
	},
};
