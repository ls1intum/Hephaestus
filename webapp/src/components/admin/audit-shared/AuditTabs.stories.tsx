import type { Meta, StoryObj } from "@storybook/react";
import { expect } from "storybook/test";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

/**
 * The audit log's tab shell. Guards the layout of the registry `Tabs` as this page uses it: the
 * orientation-dependent styles are driven by `data-orientation`, and a variant that silently fails to
 * match lays the whole page out sideways with no error anywhere.
 */
function AuditTabsHarness() {
	return (
		<Tabs className="gap-4" defaultValue="signins" data-testid="root">
			<TabsList className="h-10 w-full p-1 sm:w-fit" data-testid="list">
				<TabsTrigger value="signins">Access</TabsTrigger>
				<TabsTrigger value="settings">Settings</TabsTrigger>
			</TabsList>
			<TabsContent value="signins">
				<div data-testid="panel" className="h-24 rounded-md border" />
			</TabsContent>
		</Tabs>
	);
}

function box(root: HTMLElement, testId: string): DOMRect {
	const element = root.querySelector(`[data-testid="${testId}"]`);
	if (!element) throw new Error(`missing [data-testid="${testId}"]`);
	return element.getBoundingClientRect();
}

const meta = {
	title: "Admin/Audit/AuditTabs",
	component: AuditTabsHarness,
	parameters: { layout: "padded" },
} satisfies Meta<typeof AuditTabsHarness>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The panel sits BELOW the picker. A row layout puts them side by side and strands white space. */
export const PanelSitsBelowThePicker: Story = {
	play: async ({ canvasElement }) => {
		const root = canvasElement.querySelector('[data-testid="root"]') as HTMLElement;
		await expect(getComputedStyle(root).flexDirection).toBe("column");

		const list = box(canvasElement, "list");
		const panel = box(canvasElement, "panel");
		await expect(panel.top).toBeGreaterThanOrEqual(list.bottom);
		// One gap, from the Tabs root. A second one on TabsContent stacks into a visible hole.
		await expect(Math.round(panel.top - list.bottom)).toBeLessThanOrEqual(20);
	},
};

/** Content-width on desktop — a two-segment pill stretched across the page reads as broken chrome. */
export const PickerIsContentWidthOnDesktop: Story = {
	play: async ({ canvasElement }) => {
		const list = box(canvasElement, "list");
		await expect(list.width).toBeLessThan(canvasElement.getBoundingClientRect().width / 2);
	},
};
