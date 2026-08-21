import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen, userEvent } from "storybook/test";
import { AreaPill } from "@/components/admin/practice-catalog/AreaPill";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import { DrawerBody, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { DetailDrawerHeader } from "./DetailDrawerHeader";

/**
 * The dismiss is a `DrawerClose`, so it closes through the same path as Escape, an outside press
 * and a rightward swipe rather than a fourth one that can drift out of agreement with them.
 */
const meta = {
	component: DetailDrawerHeader,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	args: {
		children: (
			<>
				<AreaPill size="lg" slug="review-ready-work" name="Review-ready work" />
				<div className="min-w-0 flex-1 space-y-0.5">
					<DrawerTitle>Describe what changed and why</DrawerTitle>
					<DrawerDescription>Pull request</DrawerDescription>
				</div>
			</>
		),
	},
	argTypes: { children: { control: false } },
	render: (args) => (
		<Stateful initial={[{ kind: "practice", id: "describe-what-and-why" }]}>
			{(stack, setStack) => (
				<DetailDrawerStack stack={stack} onClose={(depth) => setStack(stack.slice(0, depth))}>
					{() => (
						<>
							<DetailDrawerHeader {...args} />
							<DrawerBody>
								<p className="text-sm text-muted-foreground">Body.</p>
							</DrawerBody>
						</>
					)}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof DetailDrawerHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const TopLevel: Story = {
	play: async () => {
		// At the top of a stack, dismissing returns to the page, so the control says Close.
		const close = await screen.findByRole("button", { name: "Close" });
		await expectSettledVisible(close);
		await expect(screen.queryByRole("button", { name: "Back" })).not.toBeInTheDocument();
		await userEvent.click(close);
		await expect(await screen.findByRole("heading", { name: "Practice setup" })).toBeVisible();
	},
};

export const Nested: Story = {
	args: { nested: true },
	play: async () => {
		// Below the top, dismissing returns to the drawer behind, so the control says Back.
		await expectSettledVisible(await screen.findByRole("button", { name: "Back" }));
		await expect(screen.queryByRole("button", { name: "Close" })).not.toBeInTheDocument();
	},
};

export const TitleOnly: Story = {
	args: { children: <DrawerTitle>Practice area</DrawerTitle> },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { name: "Practice area" }));
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
