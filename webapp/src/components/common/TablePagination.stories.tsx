import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import { expectGenuinelyDisabled } from "@/test/controls";
import { TablePagination } from "./TablePagination";

/**
 * Pager for a table that changes pages by calling back rather than navigating, so every control is a
 * real `<button disabled>` instead of a dimmed anchor.
 */
const meta = {
	component: TablePagination,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { page: 0, totalPages: 5, onPageChange: fn() },
} satisfies Meta<typeof TablePagination>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Reads the pager back as a reader sees it: the numbered tokens in order, with `▸` marking the one
 * carrying `aria-current="page"` and `…` standing for a gap. The window, the gaps and which page is
 * current are the whole of this component's logic, and this is the only place it is stated.
 */
async function expectTokens(canvasElement: HTMLElement, expected: string) {
	const canvas = within(canvasElement);
	const nav = canvas.getByRole("navigation");
	const tokens = [...nav.querySelectorAll("li")]
		.map((item) => {
			const button = item.querySelector("button");
			if (button == null) return "…";
			const label = button.getAttribute("aria-label") ?? "";
			// The two boundary controls are not part of the window.
			if (label.startsWith("Go to previous") || label.startsWith("Go to next")) return null;
			return `${button.getAttribute("aria-current") === "page" ? "▸" : ""}${button.textContent}`;
		})
		.filter((token) => token != null);
	await expect(tokens.join(" ")).toBe(expected);
}

/** Every page gets a token while there are seven or fewer. */
export const FirstPage: Story = {
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "▸1 2 3 4 5");
	},
};

export const MiddlePage: Story = {
	args: { page: 2 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 2 ▸3 4 5");
	},
};

/** Seven is the last count that still fits whole: every page keeps its own token. */
export const SevenPagesFitUnwindowed: Story = {
	args: { page: 3, totalPages: 7 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 2 3 ▸4 5 6 7");
	},
};

/** Eight is the first that does not, so the window opens — one page later than the story above. */
export const EightPagesOpenTheWindow: Story = {
	args: { page: 1, totalPages: 8 },
	play: async ({ canvasElement }) => {
		// Also the near-the-start shape: pages 1–3 are already adjacent, so the gap opens only after
		// them. An ellipsis standing between two consecutive pages would be a lie about what is hidden.
		await expectTokens(canvasElement, "1 ▸2 3 … 8");
	},
};

/** Past that the window collapses to first, last and the current neighbourhood. */
export const Windowed: Story = {
	args: { page: 7, totalPages: 20 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 … 7 ▸8 9 … 20");
	},
};

/** At the ends the window has no room to open on one side, so there is one gap rather than two. */
export const WindowedAtTheEnd: Story = {
	args: { page: 19, totalPages: 20 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 … 19 ▸20");
	},
};

/**
 * The gap opens only where pages are actually missing: with the current page three from the start,
 * `1 2 3 4` runs unbroken and only the tail is elided.
 */
export const WindowedWithOneAdjacentGap: Story = {
	args: { page: 2, totalPages: 20 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 2 ▸3 4 … 20");
	},
};

/**
 * A hole of exactly one page shows that page rather than eliding it: an ellipsis standing for page
 * 2 alone would be wider than the number it replaces, and unclickable where the number is not.
 */
export const SinglePageHoleIsFilled: Story = {
	args: { page: 3, totalPages: 8 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 2 3 ▸4 5 … 8");
	},
};

/** Two hidden pages is where eliding starts paying for itself. */
export const TwoPageHoleStillElides: Story = {
	args: { page: 4, totalPages: 9 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 … 4 ▸5 6 … 9");
	},
};

/** One page is no choice at all, so the pager renders nothing. */
export const SinglePage: Story = {
	args: { totalPages: 1 },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByRole("navigation")).toBeNull();
	},
};

/**
 * The boundary control is `disabled`, not dimmed: it is skipped by Tab and reported as unavailable,
 * rather than being announced as a working button that silently does nothing (WCAG 2.2 SC 4.1.2).
 */
export const BoundaryControlsAreTrulyDisabled: Story = {
	args: { page: 0, totalPages: 5 },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Go to previous page" }));
		await expect(canvas.getByRole("button", { name: "Go to next page" })).toBeEnabled();
	},
};
