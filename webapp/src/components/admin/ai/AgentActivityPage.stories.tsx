import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectPageReflows, expectTargetSize } from "@/test/reflow";
import { AgentActivityPage } from "./AgentActivityPage";
import { mockJobs } from "./story-mock-data";

/** Enough pages that the pagination windows (first, last, current ±1, ellipsis gaps). */
const TOTAL_PAGES = 12;

function handlers(totalPages = TOTAL_PAGES) {
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

/**
 * Every AI run in the workspace: a status filter, the runs table, and — once there is more than one
 * page — a windowed pager.
 */
const meta = {
	component: AgentActivityPage,
	parameters: { layout: "fullscreen", msw: { handlers: handlers() } },
	tags: ["autodocs"],
	args: { workspaceSlug: "acme" },
} satisfies Meta<typeof AgentActivityPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Twelve pages of runs, so the pager shows its full first/last/±1 window. */
export const Default: Story = {};

/** A single page of runs — the pager is hidden entirely rather than rendered inert. */
export const SinglePage: Story = {
	parameters: { msw: { handlers: handlers(1) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByRole("table");
		await expect(canvas.queryByRole("navigation", { name: "pagination" })).toBeNull();
	},
};

/**
 * The runs page at the WCAG 2.2 SC 1.4.10 reflow width (320 CSS px).
 *
 * What this page adds over `AgentJobsTable`'s own reflow story — which is where the runs table's
 * horizontal-scroll exception is stated — is the pager: it has to stay whole inside the viewport,
 * with every target still meeting SC 2.5.8's 24 x 24 px minimum.
 */
export const MobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByRole("table");

		await expectPageReflows();

		// By role: the pager's controls change the page by calling back, so they are real buttons —
		// including the boundary ones, which are `disabled` rather than dimmed anchors.
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
 * SC 1.4.4 Resize Text at 200 %: text-only zoom, which grows the `rem`-sized pager targets without
 * giving the page any more room. The pager must wrap rather than push the document sideways — that,
 * rather than the reflow width, is what `flex-wrap` on the pager is for, and it is the case that
 * actually fails without it.
 *
 * Its own story because the only way to simulate text-only zoom is to set the root font size, which
 * is a property of the runner's whole document rather than of this canvas. Keeping it alone means
 * nothing else is being measured against a 32 px root; the `finally` puts it back even on failure,
 * so a red run cannot leave every story after it zoomed.
 */
export const PagerAtDoubleTextSize: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByRole("table");
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
