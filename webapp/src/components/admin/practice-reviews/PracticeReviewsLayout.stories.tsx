import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { PageLayout } from "@/components/core/PageLayout";
import { withStandardPage } from "@/stories/decorators";
import { PracticeReviewsHeader } from "./PracticeReviewsLayout";

// `aria-current` draws the selected tab, and TanStack's `Link` also sets it on any link it considers
// active — `...isActive && { "aria-current": "page" }` is spread *after* the caller's props in
// `link.js`, so an explicit `aria-current={undefined}` cannot turn it off. Two mechanisms decide one
// attribute, so the stories below assert the *count* of current tabs, not which one it is.
const meta = {
	title: "Workspace admin/Practice reviews/Navigation",
	component: PracticeReviewsHeader,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		activeSection: "reviews",
	},
	decorators: [
		(Story) => (
			<PageLayout>
				<Story />
			</PageLayout>
		),
		withStandardPage,
	],
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeReviewsHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

async function expectOnlyCurrent(canvasElement: HTMLElement, name: string) {
	const nav = within(canvasElement).getByRole("navigation", {
		name: "Practice review sections",
	});
	expect(within(nav).getAllByRole("link", { current: "page" })).toHaveLength(1);
	within(nav).getByRole("link", { name, current: "page" });
}

export const Reviews: Story = {
	play: async ({ canvasElement }) => {
		await expectOnlyCurrent(canvasElement, "Reviews");
	},
};

export const Observations: Story = {
	args: { activeSection: "observations" },
	play: async ({ canvasElement }) => {
		await expectOnlyCurrent(canvasElement, "Observations");
	},
};

export const Delivery: Story = {
	args: { activeSection: "delivery" },
	play: async ({ canvasElement }) => {
		await expectOnlyCurrent(canvasElement, "Delivery");
	},
};

/**
 * Reviewed work is reached from any of the three sections, so it claims none of them: marking one
 * would tell the reader they had navigated somewhere they had not.
 */
export const ReviewedWork: Story = {
	args: { activeSection: undefined },
	play: async ({ canvasElement }) => {
		const nav = within(canvasElement).getByRole("navigation", {
			name: "Practice review sections",
		});
		expect(within(nav).queryAllByRole("link", { current: "page" })).toHaveLength(0);
	},
};
