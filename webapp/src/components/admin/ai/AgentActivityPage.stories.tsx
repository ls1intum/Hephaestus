import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectPageReflows, expectTablesScrollInPlace, expectTargetSize } from "@/test/reflow";
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
 * The runs table keeps its own horizontal scroll — the standard's documented data-table exception —
 * without letting that overflow reach the page, and the pager stays inside the viewport.
 *
 * The second half also covers SC 1.4.4 Resize Text at 200 %. The pager's targets are `rem`-sized, so
 * text-only zoom grows them while the viewport does not; that, rather than the reflow width, is what
 * `flex-wrap` on the pager is for, and it is the case that actually fails without it.
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
		await expectTablesScrollInPlace(canvasElement);

		// Every pager target is inside the viewport and still meets SC 2.5.8's 24 x 24 px minimum.
		// By role: the pager's controls change the page by calling back, so they are real buttons —
		// including the boundary ones, which are `disabled` rather than dimmed anchors.
		const pager = canvas.getByRole("navigation", { name: "pagination" });
		const targets = within(pager).getAllByRole("button");
		await expect(targets.length).toBeGreaterThan(2);
		for (const target of targets) {
			await expect(target.getBoundingClientRect().right).toBeLessThanOrEqual(window.innerWidth + 1);
			await expectTargetSize(target);
		}

		// SC 1.4.4 Resize Text at 200 %: text-only zoom, which grows the `rem`-sized pager targets
		// without giving the page any more room. The pager must wrap rather than push the document
		// sideways. Restored in a `finally` so one failure cannot leave the runner's page zoomed.
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
