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

export const ArrivesWithAnEnterTransition: Story = {
	args: { stack: [] },
	parameters: { chromatic: { disableSnapshot: true } },
	render: (args) => (
		<Stateful initial={args.stack}>
			{(stack, setStack) => (
				<>
					<Button onClick={() => setStack([{ kind: "practice", id: "describe-what-and-why" }])}>
						Open a level
					</Button>
					<DetailDrawerStack
						{...args}
						stack={stack}
						onClose={(depth) => setStack(stack.slice(0, depth))}
					/>
				</>
			)}
		</Stateful>
	),
	play: async () => {
		// Observed rather than polled: the starting style lasts one frame, which `waitFor` steps over.
		// It is also what has to be asserted — a level mounts when its entry appears, so it mounts
		// already open, and Base UI seeds `mounted` from `open`, which means the branch that sets
		// `starting` never runs and the panel is simply *there*, at rest, with no transition at all.
		let started = false;
		const observer = new MutationObserver((records) => {
			for (const record of records) {
				for (const node of record.addedNodes) {
					if (!(node instanceof HTMLElement)) continue;
					const popup = node.matches('[data-slot="drawer-popup"]')
						? node
						: node.querySelector('[data-slot="drawer-popup"]');
					if (popup?.hasAttribute("data-starting-style")) started = true;
				}
			}
		});
		observer.observe(document.body, { childList: true, subtree: true });

		await userEvent.click(screen.getByRole("button", { name: "Open a level" }));
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		observer.disconnect();
		await expect(started).toBe(true);
	},
};

export const CoveredLevelKeepsAColumnReadable: Story = {
	args: {
		stack: [
			{ kind: "area", id: "review-ready-work" },
			{ kind: "practice", id: "describe-what-and-why" },
		],
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		// The class rather than the computed value: this suite runs under `prefers-reduced-motion`,
		// which zeroes `--peek` on purpose, so the resolved width proves nothing here. What broke was
		// upstream of that — `cn()` dedupes an arbitrary custom property by name and keeps the last,
		// so a `--peek` default declared after the size variant silently replaced it and the column
		// was 1rem, narrower than the panel's own padding.
		await expect(popups()[0].className).toContain("[--peek:4rem]");
	},
};

export const DismissedLevelDoesNotComeBack: Story = {
	args: {
		stack: [
			{ kind: "area", id: "review-ready-work" },
			{ kind: "practice", id: "describe-what-and-why" },
		],
	},
	parameters: { chromatic: { disableSnapshot: true } },
	render: (args) => (
		<Stateful initial={args.stack}>
			{(stack, setStack) => (
				<DetailDrawerStack
					{...args}
					stack={stack}
					// The app's `onClose` navigates, so the stack shrinks a frame or more after the exit
					// completes. Synchronous state hides the bug this story exists for.
					onClose={(depth) => {
						args.onClose(depth);
						requestAnimationFrame(() =>
							requestAnimationFrame(() => setStack(stack.slice(0, depth))),
						);
					}}
				/>
			)}
		</Stateful>
	),
	play: async () => {
		await expectSettledVisible(await screen.findByText("practice · describe-what-and-why"));
		const parent = popups()[0];
		await userEvent.click(screen.getByRole("button", { name: "Back" }));

		// Watch every frame until the level is gone. Clearing the closing depth on the completion
		// frame re-opened it while the navigation was still in flight: it popped back in, and this
		// level snapped to its stepped-back position and animated forward a second time.
		const nested: string[] = [];
		for (let frame = 0; frame < 40 && popups().length > 1; frame++) {
			await new Promise((resolve) => requestAnimationFrame(resolve));
			nested.push(getComputedStyle(parent).getPropertyValue("--nested-drawers").trim());
		}
		await waitFor(() => expect(popups()).toHaveLength(1));
		await expect(nested.indexOf("1")).toBe(-1);
	},
};

/**
 * Every region a panel is built from reaches both edges of it, with nothing left over underneath.
 * The footer is the one that goes wrong quietly: written as a sticky bar inside the scrolling body
 * it inherits that body's padding, so it floats short of each edge and leaves a strip of dead space
 * below itself at the end of the scroll.
 */
export const RegionsReachThePanelEdges: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { disableSnapshot: true } },
	args: {
		children: (entry, level) => (
			<>
				<DetailDrawerHeader nested={level.nested}>
					<DrawerTitle>{entry.id}</DrawerTitle>
				</DetailDrawerHeader>
				<DrawerBody>
					{Array.from({ length: 20 }, (_, index) => (
						<p key={index}>Enough content that the body genuinely scrolls.</p>
					))}
				</DrawerBody>
				<DrawerFooter>
					<Button variant="outline">Cancel</Button>
					<Button>Save changes</Button>
				</DrawerFooter>
			</>
		),
	},
	play: async () => {
		await expectSettledVisible(await screen.findByText("describe-what-and-why"));
		const [popup] = popups();
		const panel = popup.getBoundingClientRect();
		const region = (slot: string) =>
			popup.querySelector<HTMLElement>(`[data-slot="${slot}"]`)?.getBoundingClientRect();

		const insets = ["drawer-header", "drawer-body", "drawer-footer"].map((slot) => {
			const box = region(slot);
			// The 1px left is the panel's own border, which every region sits inside.
			return `${slot} ${Math.round((box?.left ?? 0) - panel.left)}/${Math.round(panel.right - (box?.right ?? 0))}`;
		});
		await expect(insets).toEqual(["drawer-header 1/0", "drawer-body 1/0", "drawer-footer 1/0"]);
		await expect(Math.round(panel.bottom - (region("drawer-footer")?.bottom ?? 0))).toBe(0);

		// A form disables every control while it submits, and then nothing inside the scrolling
		// region is focusable — so the region itself has to be.
		await expect(popup.querySelector('[data-slot="drawer-body"]')).toHaveAttribute("tabindex", "0");
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
