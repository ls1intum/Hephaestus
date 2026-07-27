import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { expect, screen, userEvent, waitFor, within } from "storybook/test";
import {
	ACTION_LABELS,
	ENTITY_TYPE_LABELS,
} from "@/components/admin/config-audit/config-audit-format";
import { FacetMultiSelect } from "@/components/common/FacetMultiSelect";
import { AuditDateFacet } from "./AuditDateFacet";
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
			<FacetMultiSelect
				title="Setting"
				options={ENTITY_OPTIONS}
				selected={entityTypes}
				onChange={setEntityTypes}
			/>
			<FacetMultiSelect
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

/** Clearing one facet leaves the others standing, and the control is reachable by keyboard. */
export const ClearsOneFacetOnly: Story = {
	args: { initialEntityTypes: ["WORKSPACE_FEATURES"], initialActions: ["UPDATED"] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox", { name: /^Setting/i }));
		// Tabbed to, not clicked: Base UI's own Combobox.Clear carries tabIndex -1, so reaching it by
		// mouse says nothing about whether a keyboard user can. The popup moves focus to its search
		// field asynchronously, so wait for that first — tabbing before it lands measures from the
		// wrong starting point.
		const clear = await screen.findByRole("button", { name: /clear selection/i });
		const search = await screen.findByPlaceholderText("Search…");
		await waitFor(() => expect(search).toHaveFocus());
		await userEvent.tab();
		await expect(clear).toHaveFocus();
		await userEvent.keyboard("{Enter}");
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

/**
 * A date range counts as a filter: the toolbar offers Reset for it, exactly as it does for the facet
 * pickers. How the range itself reads on the trigger is `AuditDateFacet.stories.tsx`'s to say, in
 * both its closed and open-ended shapes.
 */
export const DateRangeSelected: Story = {
	args: {
		initialRange: { from: new Date("2026-07-01"), to: new Date("2026-07-08") },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /reset/i })).toBeInTheDocument();
	},
};

/** A half-chosen range is still a filter, so Reset is still offered. */
export const DateRangeOpenEnded: Story = {
	args: { initialRange: { from: new Date("2026-07-01"), to: undefined } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /reset/i })).toBeInTheDocument();
	},
};
