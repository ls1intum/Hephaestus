import type { Meta, StoryObj } from "@storybook/react";
import type { CohortPracticeStatus } from "@/api/types.gen";
import { CohortHealthCard } from "./CohortHealthCard";

const meta = {
	component: CohortHealthCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof CohortHealthCard>;

export default meta;
type Story = StoryObj<typeof meta>;

const health = (over: Partial<CohortPracticeStatus>): CohortPracticeStatus => ({
	name: "Clear PR description",
	slug: "clear-pr",
	strengthCount: 6,
	developingCount: 5,
	mixedCount: 5,
	noActivityCount: 5,
	...over,
});

export const Default: Story = { args: { health: health({}) } };

export const Suppressed: Story = {
	args: { health: { name: "Reproduce before fixing", slug: "repro-first", suppressed: true } },
};

export const NoActivity: Story = {
	args: {
		health: health({ strengthCount: 0, developingCount: 0, mixedCount: 0, noActivityCount: 0 }),
	},
};

export const LongName: Story = {
	args: {
		health: health({
			name: "Write descriptive pull request titles and summaries that explain the change",
		}),
	},
};
