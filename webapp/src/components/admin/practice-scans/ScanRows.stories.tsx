import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { Table, TableHeader, TableRow } from "@/components/ui/table";
import { withStandardPage } from "@/stories/decorators";

import { CANCELLED_SCAN, COMPLETE_SCAN, PARTIAL_SCAN, QUEUED_SCAN, RUNNING_SCAN } from "./fixtures";
import { ScanColumnHeads, ScanRows } from "./ScanRows";

const meta = {
	component: ScanRows,
	decorators: [withStandardPage],
	args: {
		workspaceSlug: "aet",
		scans: [QUEUED_SCAN, RUNNING_SCAN, COMPLETE_SCAN, PARTIAL_SCAN, CANCELLED_SCAN],
	},
	argTypes: { scans: { control: false } },
	render: (args) => (
		<Table bordered aria-label="Scans">
			<TableHeader>
				<TableRow>
					<ScanColumnHeads />
				</TableRow>
			</TableHeader>
			<ScanRows {...args} />
		</Table>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof ScanRows>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		const rows = canvas.getAllByRole("row").slice(1);
		await expect(rows).toHaveLength(5);
		const [queued, running, complete] = rows;
		if (!queued || !running || !complete) {
			return;
		}
		await expect(within(queued).getByText("Queued")).toBeVisible();
		await expect(within(complete).getByText("main")).toBeVisible();
		await expect(within(complete).getByText("· 4f2a9c1", { exact: false })).toBeVisible();
		await expect(within(running).getByText("Ada Lovelace")).toBeVisible();
	},
};
