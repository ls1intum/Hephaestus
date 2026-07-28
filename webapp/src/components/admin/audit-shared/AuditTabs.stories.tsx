import type { Meta, StoryObj } from "@storybook/react";
import { expect } from "storybook/test";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

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

export const PanelSitsBelowThePicker: Story = {
	play: async ({ canvasElement }) => {
		const root = canvasElement.querySelector('[data-testid="root"]') as HTMLElement;
		await expect(getComputedStyle(root).flexDirection).toBe("column");

		const list = box(canvasElement, "list");
		const panel = box(canvasElement, "panel");
		await expect(panel.top).toBeGreaterThanOrEqual(list.bottom);
		await expect(Math.round(panel.top - list.bottom)).toBeLessThanOrEqual(20);
	},
};

export const PickerIsContentWidthOnDesktop: Story = {
	play: async ({ canvasElement }) => {
		const list = box(canvasElement, "list");
		await expect(list.width).toBeLessThan(canvasElement.getBoundingClientRect().width / 2);
	},
};
