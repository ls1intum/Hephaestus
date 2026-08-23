import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, waitFor } from "storybook/test";
import { DetailDrawerHeader } from "@/components/core/detail-drawer/DetailDrawerHeader";
import { Button } from "@/components/ui/button";
import { DrawerBody, DrawerDescription, DrawerFooter, DrawerTitle } from "@/components/ui/drawer";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { DetailDrawerStack } from "./DetailDrawerStack";

const popups = () =>
	Array.from(document.querySelectorAll<HTMLElement>('[data-slot="drawer-popup"]'));

/** The app-wide alternative to sending someone to another page to look at one row. */
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

export const DismissedLevelSlidesOut: Story = {
	play: async () => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		const [popup] = popups();
		await userEvent.click(screen.getByRole("button", { name: "Close" }));
		// Still mounted and still carrying its content, animating out. Dropping it on the URL change
		// makes a dismissal vanish in one frame instead.
		await expect(popup).toHaveAttribute("data-ending-style");
		await expect(popup.textContent).toContain("practice · describe-what-and-why");
		await waitFor(() => expect(popups()).toHaveLength(0));
	},
};

export const PerLevelDataSurvivesDismissal: Story = {
	render: (args) => (
		<Stateful initial={args.stack}>
			{(stack, setStack) => {
				const perLevel = stack.map((entry) => ({ heading: entry.id }));
				return (
					<DetailDrawerStack
						stack={stack}
						onClose={(depth) => {
							args.onClose(depth);
							setStack(stack.slice(0, depth));
						}}
					>
						{(_entry, level) => (
							<>
								<DetailDrawerHeader nested={level.nested}>
									<DrawerTitle>{perLevel[level.depth].heading}</DrawerTitle>
								</DetailDrawerHeader>
								<DrawerBody>
									<p className="text-sm text-muted-foreground">Level content.</p>
								</DrawerBody>
							</>
						)}
					</DetailDrawerStack>
				);
			}}
		</Stateful>
	),
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByText("describe-what-and-why"));
		await userEvent.click(screen.getByRole("button", { name: "Close" }));
		await waitFor(() => expect(popups()).toHaveLength(0));
		await expect(args.onClose).toHaveBeenCalledWith(0);
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
		// Both `waitFor`: the level animates out first and the URL follows, so neither the popup
		// leaving nor the callback firing happens on the click itself.
		await waitFor(() => expect(popups()).toHaveLength(0));
		await expect(args.onClose).toHaveBeenCalledWith(0);
	},
};

export const GuardedLevelRefusesCasualDismissal: Story = {
	args: { guardedKinds: ["practice"] },
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		// The two gestures that discard without asking. `PressingThePageDismisses` is the control: the
		// same press ends an unguarded level. `data-ending-style` rather than the popup going away,
		// because the exit is animated — the level is still mounted for the length of it either way,
		// and `DismissedLevelSlidesOut` is what pins that the attribute lands on the gesture itself.
		await userEvent.keyboard("{Escape}");
		await expect(popups()[0]).not.toHaveAttribute("data-ending-style");
		await userEvent.pointer({
			target: document.elementFromPoint(20, 200) as Element,
			coords: { clientX: 20, clientY: 200 },
			keys: "[MouseLeft]",
		});
		await expect(popups()[0]).not.toHaveAttribute("data-ending-style");
		await expect(args.onClose).not.toHaveBeenCalled();

		// The level's own control still works, or there would be no way out.
		await userEvent.click(screen.getByRole("button", { name: "Close" }));
		await waitFor(() => expect(args.onClose).toHaveBeenCalledWith(0));
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
		await waitFor(() => expect(args.onClose).toHaveBeenCalledWith(1));
		await expect(await screen.findByText("area · review-ready-work")).toBeVisible();
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
