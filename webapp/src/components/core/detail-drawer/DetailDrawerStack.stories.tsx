import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { DetailDrawerPanel } from "@/components/core/detail-drawer/DetailDrawerPanel";
import { Button } from "@/components/ui/button";
import { expectSettledVisible } from "@/test/overlay";
import { DetailDrawerStack } from "./DetailDrawerStack";

const popups = () =>
	Array.from(document.querySelectorAll<HTMLElement>('[data-slot="drawer-popup"]'));

/**
 * The app-wide alternative to sending someone to another page to look at one row.
 *
 * A level is a `detail` entry in the URL, so the stack is shareable, reloadable, and the browser's
 * Back button pops exactly one drawer. Base UI reads nesting off the React tree rather than the
 * DOM, which is why each level renders the next as its own child — and why the drawers behind the
 * frontmost one step back and dim without any coordination code here.
 */
const meta = {
	component: DetailDrawerStack,
	parameters: { layout: "fullscreen", chromatic: { viewports: [320, 1440] } },
	args: {
		stack: [{ kind: "practice", id: "describe-what-and-why" }],
		onClose: fn(),
		children: (entry, depth) => (
			<DetailDrawerPanel
				title={`${entry.kind} · ${entry.id}`}
				description={`Level ${depth + 1}`}
				footer={<Button>Primary action</Button>}
			>
				<p className="text-sm text-muted-foreground">
					Whatever this level is about. The page that opened it is still mounted behind.
				</p>
			</DetailDrawerPanel>
		),
	},
	render: (args) => <DetailDrawerStack {...args} />,
	tags: ["autodocs"],
} satisfies Meta<typeof DetailDrawerStack>;

export default meta;
type Story = StoryObj<typeof meta>;

export const OneLevel: Story = {
	play: async () => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		// The outermost level returns to the page, so it closes rather than going back one drawer.
		await expect(screen.getByRole("button", { name: "Close" })).toBeEnabled();
		await expect(popups()).toHaveLength(1);
	},
};

export const Closed: Story = {
	args: { stack: [] },
	play: async () => {
		await expect(popups()).toHaveLength(0);
	},
};

export const TwoLevels: Story = {
	args: {
		stack: [
			{ kind: "area", id: "review-ready-work" },
			{ kind: "practice", id: "describe-what-and-why" },
		],
	},
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		const [parent, frontmost] = popups();
		// The level behind knows it is behind, which is what drives the step-back and dim.
		await expect(parent).toHaveAttribute("data-nested-drawer-open");
		await expect(getComputedStyle(parent).getPropertyValue("--nested-drawers").trim()).toBe("1");
		await expect(getComputedStyle(frontmost).getPropertyValue("--nested-drawers").trim()).toBe("0");
		const back = screen.getByRole("button", { name: "Back" });
		await userEvent.click(back);
		await expect(args.onClose).toHaveBeenCalledWith(1);
	},
};

export const ThreeLevels: Story = {
	args: {
		stack: [
			{ kind: "area", id: "review-ready-work" },
			{ kind: "practice", id: "describe-what-and-why" },
			{ kind: "evidence", id: "pull-request-body" },
		],
	},
	play: async () => {
		await expectSettledVisible(await screen.findByText("evidence · pull-request-body"));
		await expect(popups()).toHaveLength(3);
	},
};

export const NarrowViewport: Story = {
	args: {
		stack: [
			{ kind: "area", id: "review-ready-work" },
			{ kind: "practice", id: "describe-what-and-why" },
		],
	},
	globals: { theme: "dark" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		// A partial cover is unreadable on a phone, so a detail drawer takes the whole width there.
		const [, frontmost] = popups();
		await expect(frontmost.getBoundingClientRect().width).toBe(window.innerWidth);
	},
};
