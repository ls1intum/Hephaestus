import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { Practice } from "@/api/types.gen";
import { mockPractices } from "@/components/admin/practices/story-mock-data";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { expectNoPanelOverflow } from "@/test/reflow";
import { WorkspacePracticePanel, type WorkspacePracticeState } from "./WorkspacePracticePanel";

const practice = mockPractices[0] as Practice;

type ReadyState = Extract<WorkspacePracticeState, { status: "ready" }>;

const ready = (over: Partial<ReadyState> = {}): ReadyState => ({
	status: "ready",
	practice,
	definitionOptions: mockPracticeDefinitionOptions,
	areaName: "Review-ready work",
	...over,
});

const meta = {
	title: "Workspace admin/Practices/Workspace practice",
	component: WorkspacePracticePanel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	args: { workspaceSlug: "demo", state: ready() },
	argTypes: { state: { control: false } },
	render: (args) => (
		<Stateful initial={[{ kind: "practice", id: practice.slug }]}>
			{(stack, setStack) => (
				<DetailDrawerStack stack={stack} onClose={(depth) => setStack(stack.slice(0, depth))}>
					{(_entry, level) => <WorkspacePracticePanel {...args} nested={level.nested} />}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof WorkspacePracticePanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async () => {
		const edit = await screen.findByRole("link", { name: "Edit practice" });
		await expectSettledVisible(edit);
		// Editing is a route, not another drawer level, so the link is a real path.
		// The stack rides along so returning from the editor lands on this panel, not a bare list.
		await expect(edit.getAttribute("href")).toContain(`/w/demo/admin/practices/${practice.slug}`);
		// Level 2 is the panel's own title; criteria headings render below it at level 4.
		await expect(screen.getByRole("heading", { name: practice.name, level: 2 })).toBeVisible();
	},
};

export const InheritedAutonomy: Story = {
	args: {
		state: ready({
			practice: {
				...practice,
				autonomy: {
					effective: "HUMAN_APPROVAL",
					inherited: true,
					source: "AREA",
				},
			},
		}),
	},
	play: async () => {
		// An inherited value says where it came from, so the reader knows where to go to change it.
		await expectSettledVisible(await screen.findByText(/Follows Review-ready work/));
	},
};

export const Unassigned: Story = {
	args: {
		state: ready({ practice: { ...practice, areaSlug: undefined }, areaName: undefined }),
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async () => {
		// A skeleton holding the shape the definition will take, so nothing jumps when it lands.
		// It is `aria-hidden`, so the proof is the DOM, not a role.
		const panel = document.querySelector<HTMLElement>('[data-slot="drawer-popup"]');
		await expectSettledVisible(panel as HTMLElement);
		await expect(panel?.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0);
		await expect(screen.queryByRole("status")).not.toBeInTheDocument();
	},
};

export const FailedToLoad: Story = {
	args: { state: { status: "error", error: new Error("offline"), onRetry: fn() } },
	play: async ({ args }) => {
		const retry = await screen.findByRole("button", { name: "Retry" });
		await expectSettledVisible(retry);
		await userEvent.click(retry);
		const state = args.state;
		await expect(state.status === "error" && state.onRetry).toHaveBeenCalledOnce();
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		const [panel] = document.querySelectorAll<HTMLElement>('[data-slot="drawer-popup"]');
		await expectSettledVisible(panel);
		await expectNoPanelOverflow(panel);
	},
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
