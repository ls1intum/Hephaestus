import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import { Button } from "@/components/ui/button";
import { DrawerBody, DrawerDescription, DrawerFooter, DrawerTitle } from "@/components/ui/drawer";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
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
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	args: {
		stack: [{ kind: "practice", id: "describe-what-and-why" }],
		onClose: fn(),
		children: (entry, level) => (
			<>
				<DetailDrawerHeader nested={level.nested}>
					<div className="min-w-0 flex-1 space-y-0.5">
						<DrawerTitle>{`${entry.kind} · ${entry.id}`}</DrawerTitle>
						<DrawerDescription>{`Level ${level.depth + 1}`}</DrawerDescription>
					</div>
				</DetailDrawerHeader>
				<DrawerBody>
					<p className="text-sm text-muted-foreground">
						Whatever this level is about. The page that opened it is still mounted behind, and stays
						readable in the column the stack leaves showing.
					</p>
				</DrawerBody>
				<DrawerFooter>
					<Button>Primary action</Button>
				</DrawerFooter>
			</>
		),
	},
	argTypes: {
		// Neither takes a useful control: one is an array of records, the other a render prop that
		// the story would break by editing.
		stack: { control: false },
		children: { control: false },
	},
	// Stateful so a dismiss actually dismisses. A frozen `stack` with an `fn()` leaves Escape, an
	// outside press and the header control all looking broken while nothing is wrong.
	render: (args) => (
		<Stateful initial={args.stack}>
			{(stack, setStack) => (
				<DetailDrawerStack
					{...args}
					stack={stack}
					onClose={(depth) => {
						args.onClose(depth);
						setStack(stack.slice(0, depth));
					}}
				/>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof DetailDrawerStack>;

export default meta;
type Story = StoryObj<typeof meta>;

export const OneLevel: Story = {
	play: async () => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		await expect(popups()).toHaveLength(1);
	},
};

export const PressingThePageDismisses: Story = {
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		// A press on the page beside the panel is the fastest way out, and the reason the panel is
		// narrower than the viewport.
		await userEvent.pointer({
			target: document.elementFromPoint(20, 200) as Element,
			coords: { clientX: 20, clientY: 200 },
			keys: "[MouseLeft]",
		});
		await expect(args.onClose).toHaveBeenCalledWith(0);
		await expect(popups()).toHaveLength(0);
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
		// One level popped, not the whole stack.
		await expect(await screen.findByText("area · review-ready-work")).toBeVisible();
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
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		// A partial cover is unreadable on a phone, so a detail drawer takes the whole width there.
		const [, frontmost] = popups();
		await expect(frontmost.getBoundingClientRect().width).toBe(window.innerWidth);
	},
};

export const DarkMode: Story = {
	args: {
		stack: [
			{ kind: "area", id: "review-ready-work" },
			{ kind: "practice", id: "describe-what-and-why" },
		],
	},
	globals: { theme: "dark" },
};
