import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import { expectGenuinelyDisabled } from "@/test/controls";
import { TablePagination } from "./TablePagination";

const meta = {
	component: TablePagination,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { page: 0, totalPages: 5, onPageChange: fn() },
} satisfies Meta<typeof TablePagination>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Reads the pager off the accessible tree: `▸` marks `aria-current="page"`, `…` marks a gap. */
async function expectTokens(canvasElement: HTMLElement, expected: string) {
	const canvas = within(canvasElement);
	const nav = canvas.getByRole("navigation");
	const pages = within(nav).getAllByRole("button", { name: /^Go to page \d+$/ });
	const gaps = within(nav).queryAllByText("More pages");
	const tokens = [...pages, ...gaps]
		.sort((a, b) => (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1))
		.map((node) =>
			gaps.includes(node)
				? "…"
				: `${node.getAttribute("aria-current") === "page" ? "▸" : ""}${node.textContent}`,
		);
	await expect(tokens.join(" ")).toBe(expected);
}

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

export const SevenPagesFitUnwindowed: Story = {
	args: { page: 3, totalPages: 7 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 2 3 ▸4 5 6 7");
	},
};

export const EightPagesOpenTheWindow: Story = {
	args: { page: 1, totalPages: 8 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 ▸2 3 … 8");
	},
};

export const Windowed: Story = {
	args: { page: 7, totalPages: 20 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 … 7 ▸8 9 … 20");
	},
};

export const WindowedAtTheEnd: Story = {
	args: { page: 19, totalPages: 20 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 … 19 ▸20");
	},
};

export const WindowedWithOneAdjacentGap: Story = {
	args: { page: 2, totalPages: 20 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 2 ▸3 4 … 20");
	},
};

export const SinglePageHoleIsFilled: Story = {
	args: { page: 3, totalPages: 8 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 2 3 ▸4 5 … 8");
	},
};

export const TwoPageHoleStillElides: Story = {
	args: { page: 4, totalPages: 9 },
	play: async ({ canvasElement }) => {
		await expectTokens(canvasElement, "1 … 4 ▸5 6 … 9");
	},
};

export const SinglePage: Story = {
	args: { totalPages: 1 },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByRole("navigation")).toBeNull();
	},
};

export const BoundaryControlsAreTrulyDisabled: Story = {
	args: { page: 0, totalPages: 5 },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Go to previous page" }));
		await expect(canvas.getByRole("button", { name: "Go to next page" })).toBeEnabled();
	},
};
