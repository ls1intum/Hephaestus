import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { expect, screen, userEvent, within } from "storybook/test";
import {
	ACTION_LABELS,
	ENTITY_TYPE_LABELS,
} from "@/components/admin/config-audit/configAuditFormat";
import { AuditDateFacet } from "./AuditDateFacet";
import { AuditFacetFilter } from "./AuditFacetFilter";
import { AuditToolbar } from "./AuditToolbar";

// The production label maps, not retyped copies — a story asserting a label the app never renders
// documents nothing.
const ENTITY_OPTIONS = Object.entries(ENTITY_TYPE_LABELS).map(([value, label]) => ({
	value,
	label,
}));
const ACTION_OPTIONS = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }));

/**
 * Stateful harness — the toolbar is fully controlled in the app (its state lives in the URL), so the
 * stories own the state the routes normally own.
 */
function ToolbarHarness({
	initialEntityTypes = [],
	initialActions = [],
	initialRange,
}: {
	initialEntityTypes?: string[];
	initialActions?: string[];
	initialRange?: Parameters<typeof AuditDateFacet>[0]["value"];
}) {
	const [entityTypes, setEntityTypes] = useState(initialEntityTypes);
	const [actions, setActions] = useState(initialActions);
	const [range, setRange] = useState(initialRange);

	const hasFilter = entityTypes.length > 0 || actions.length > 0 || range?.from !== undefined;

	return (
		<AuditToolbar
			hasFilter={hasFilter}
			onReset={() => {
				setEntityTypes([]);
				setActions([]);
				setRange(undefined);
			}}
		>
			<AuditFacetFilter
				title="Setting"
				options={ENTITY_OPTIONS}
				selected={entityTypes}
				onChange={setEntityTypes}
			/>
			<AuditFacetFilter
				title="Action"
				options={ACTION_OPTIONS}
				selected={actions}
				onChange={setActions}
			/>
			<AuditDateFacet value={range} onChange={setRange} />
		</AuditToolbar>
	);
}

const meta = {
	title: "Admin/Audit/AuditToolbar",
	component: ToolbarHarness,
	parameters: { layout: "padded" },
} satisfies Meta<typeof ToolbarHarness>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing selected: dashed triggers only, and no Reset to click. */
export const Empty: Story = {
	args: {},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("combobox", { name: /^Setting/i })).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /reset/i })).not.toBeInTheDocument();
	},
};

/** One selection per facet — each trigger carries its own chip. */
export const WithSelection: Story = {
	args: { initialEntityTypes: ["WORKSPACE_FEATURES"], initialActions: ["UPDATED"] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Feature flags")).toBeInTheDocument();
		await expect(canvas.getByText("Updated")).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /reset/i })).toBeInTheDocument();
	},
};

/** Past two selections the trigger collapses to a count rather than growing without bound. */
export const CollapsesToCount: Story = {
	args: {
		initialEntityTypes: ["WORKSPACE_FEATURES", "AGENT_CONFIG", "WORKSPACE_ROLE"],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("3 selected")).toBeInTheDocument();
	},
};

/** Selecting a second value adds to the filter rather than replacing it — the multi-select promise. */
export const SelectsMultiple: Story = {
	args: {},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox", { name: /^Setting/i }));
		// The popup is portalled, so it is queried from the document rather than the canvas.
		await userEvent.click(await screen.findByRole("option", { name: "Feature flags" }));
		await userEvent.click(
			await screen.findByRole("option", { name: ENTITY_TYPE_LABELS.AGENT_CONFIG }),
		);
		await userEvent.keyboard("{Escape}");

		await expect(canvas.getByText("Feature flags")).toBeInTheDocument();
		await expect(canvas.getByText(ENTITY_TYPE_LABELS.AGENT_CONFIG)).toBeInTheDocument();
	},
};

/** Reset clears every facet at once and disappears with the last of them. */
export const ResetClearsEverything: Story = {
	args: { initialEntityTypes: ["WORKSPACE_FEATURES"], initialActions: ["UPDATED"] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /reset/i }));

		await expect(canvas.queryByText("Feature flags")).not.toBeInTheDocument();
		await expect(canvas.queryByText("Updated")).not.toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /reset/i })).not.toBeInTheDocument();
	},
};

/** The popup itself: checkbox affordances, and the per-facet clear that Reset used to be the only
 *  substitute for. */
export const OpenPopup: Story = {
	args: { initialEntityTypes: ["WORKSPACE_FEATURES"] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox", { name: /^Setting/i }));

		await expect(await screen.findByRole("listbox")).toBeInTheDocument();
		await expect(
			await screen.findByRole("button", { name: /clear selection/i }),
		).toBeInTheDocument();
	},
};

/** Clearing one facet must leave the others standing — the reason Reset alone was not enough. */
export const ClearsOneFacetOnly: Story = {
	args: { initialEntityTypes: ["WORKSPACE_FEATURES"], initialActions: ["UPDATED"] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox", { name: /^Setting/i }));
		await userEvent.click(await screen.findByRole("button", { name: /clear selection/i }));
		await userEvent.keyboard("{Escape}");

		await expect(canvas.queryByText(ENTITY_TYPE_LABELS.WORKSPACE_FEATURES)).not.toBeInTheDocument();
		await expect(canvas.getByText(ACTION_LABELS.UPDATED)).toBeInTheDocument();
	},
};

/** The selection is part of the trigger's accessible name, so it is not sighted-only. */
export const SelectionIsAnnounced: Story = {
	args: { initialEntityTypes: ["WORKSPACE_FEATURES"] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByRole("combobox", {
				name: `Setting: ${ENTITY_TYPE_LABELS.WORKSPACE_FEATURES}`,
			}),
		).toBeInTheDocument();
	},
};

/** A closed range and an open-ended one — the date trigger's two label shapes. */
export const DateRangeSelected: Story = {
	args: {
		initialRange: { from: new Date("2026-07-01"), to: new Date("2026-07-08") },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Jul 1 – Jul 8, 2026")).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /reset/i })).toBeInTheDocument();
	},
};

export const DateRangeOpenEnded: Story = {
	args: { initialRange: { from: new Date("2026-07-01"), to: undefined } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("From Jul 1, 2026")).toBeInTheDocument();
	},
};
