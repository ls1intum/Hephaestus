import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent } from "storybook/test";

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

export const Default: Story = {
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Go to next page" }));
		await expect(args.onPageChange).toHaveBeenCalledWith(1);
	},
};

export const Windowed: Story = {
	args: { page: 7, totalPages: 20 },
};

export const SinglePage: Story = {
	args: { totalPages: 1 },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("navigation")).toBeNull();
	},
};

export const BoundaryControls: Story = {
	play: async ({ canvas }) => {
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Go to previous page" }));
		await expect(canvas.getByRole("button", { name: "Go to next page" })).toBeEnabled();
	},
};

export const LinkNavigation: Story = {
	render: () => (
		<TablePagination
			page={1}
			totalPages={3}
			renderPageLink={(page, props) => <a {...props} href={`?status=FAILED&page=${page}`} />}
		/>
	),
	play: async ({ canvas }) => {
		const next = canvas.getByRole("link", { name: "Go to next page" });
		await expect(next).toHaveAttribute("href", "?status=FAILED&page=2");
		await expect(canvas.getByRole("link", { name: "Go to page 2" })).toHaveAttribute(
			"aria-current",
			"page",
		);
	},
};
