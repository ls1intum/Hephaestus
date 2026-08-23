import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { ARTIFACT_KIND_VALUES } from "@/lib/artifact-kinds";
import { WorkTypeLabel } from "./WorkTypeLabel";

/** The kind of work a practice reviews: one glyph, one phrase, one gap. */
const meta = {
	title: "Shared/Practice vocabulary/Work type",
	component: WorkTypeLabel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { artifactKind: "scm.pull_request" },
} satisfies Meta<typeof WorkTypeLabel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const EveryKind: Story = {
	render: () => (
		<ul className="space-y-2 text-sm">
			{ARTIFACT_KIND_VALUES.map((kind) => (
				<li key={kind}>
					<WorkTypeLabel artifactKind={kind} />
				</li>
			))}
		</ul>
	),
	play: async ({ canvas }) => {
		await expect(canvas.getAllByRole("listitem")).toHaveLength(ARTIFACT_KIND_VALUES.length);
	},
};

/** A kind this build has never heard of takes the neutral glyph rather than borrowing another's. */
export const UnknownKind: Story = {
	args: { artifactKind: "scm.something_new" },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("scm.something_new")).toBeVisible();
	},
};

export const Missing: Story = {
	args: { artifactKind: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Reviewed work")).toBeVisible();
	},
};
