import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { HEALTH_FILLED, HEALTH_SPARSE, HEALTH_SUPPRESSED } from "@/components/practices/story-data";
import {
	WorkspaceHealthCards,
	WorkspaceHealthCardsSkeleton,
} from "@/components/practices/WorkspaceHealthCards";

/**
 * Anonymous workspace health per practice area: how many developers stand at each status.
 * Counts are people per status, never named. An area below the privacy threshold says so in
 * plain words, and an area without activity is a different state from a suppressed one.
 */
const meta = {
	component: WorkspaceHealthCards,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { health: HEALTH_FILLED },
} satisfies Meta<typeof WorkspaceHealthCards>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Counts available on every area. */
export const Available: Story = {};

/** Every area below the privacy threshold states the rule instead of showing numbers. */
export const Suppressed: Story = {
	args: { health: HEALTH_SUPPRESSED },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const notes = await canvas.findAllByText(
			"Shown once five or more developers are active here, so nobody can be singled out.",
		);
		await expect(notes.length).toBeGreaterThan(0);
	},
};

/** A fresh workspace: two areas with early counts, the rest without activity yet. */
export const SparseNewWorkspace: Story = {
	args: { health: HEALTH_SPARSE },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const noData = await canvas.findAllByText("No activity in this area yet.");
		await expect(noData.length).toBeGreaterThan(0);
	},
};

/** Loading skeleton mirroring the card grid. */
export const Loading: Story = {
	render: () => <WorkspaceHealthCardsSkeleton />,
};
