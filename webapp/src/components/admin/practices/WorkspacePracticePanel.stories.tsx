import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen } from "storybook/test";
import type { Practice } from "@/api/types.gen";
import { mockPractices } from "@/components/admin/practices/story-mock-data";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
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
		await expect(edit).toHaveAttribute("href", `/w/demo/admin/practices/${practice.slug}`);
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
		await expectSettledVisible(await screen.findByText("Loading practice"));
	},
};

export const FailedToLoad: Story = {
	args: { state: { status: "error", error: new Error("offline"), onRetry: fn() } },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("button", { name: "Retry" }));
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
