import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { expect, screen, userEvent, within } from "storybook/test";
import { AuditDateFacet } from "./AuditDateFacet";
import { AuditFacetFilter } from "./AuditFacetFilter";
import { AuditToolbar } from "./AuditToolbar";

const ENTITY_OPTIONS = [
	{ value: "PRACTICE_REVIEW_SETTINGS", label: "Review settings" },
	{ value: "AI_CONFIG_BINDING", label: "AI binding" },
	{ value: "AGENT_CONFIG", label: "Agent configuration" },
	{ value: "WORKSPACE_FEATURES", label: "Feature flags" },
	{ value: "WORKSPACE_ROLE", label: "Member role" },
];

const ACTION_OPTIONS = [
	{ value: "CREATED", label: "Created" },
	{ value: "UPDATED", label: "Updated" },
	{ value: "DELETED", label: "Deleted" },
];

/**
 * Stateful harness — the toolbar is fully controlled in the app (its state lives in the URL), so the
 * stories own the state the routes normally own.
 */
function ToolbarHarness({
	initialEntityTypes = [],
	initialActions = [],
}: {
	initialEntityTypes?: string[];
	initialActions?: string[];
}) {
	const [entityTypes, setEntityTypes] = useState(initialEntityTypes);
	const [actions, setActions] = useState(initialActions);
	const [range, setRange] = useState<Parameters<typeof AuditDateFacet>[0]["value"]>(undefined);

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
				title="What changed"
				options={ENTITY_OPTIONS}
				selected={entityTypes}
				onChange={setEntityTypes}
			/>
			<AuditFacetFilter
				title="Change"
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
		await expect(canvas.getByRole("combobox", { name: /what changed/i })).toBeInTheDocument();
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
		await userEvent.click(canvas.getByRole("combobox", { name: /what changed/i }));
		// The popup is portalled, so it is queried from the document rather than the canvas.
		await userEvent.click(await screen.findByRole("option", { name: "Feature flags" }));
		await userEvent.click(await screen.findByRole("option", { name: "Agent configuration" }));
		await userEvent.keyboard("{Escape}");

		await expect(canvas.getByText("Feature flags")).toBeInTheDocument();
		await expect(canvas.getByText("Agent configuration")).toBeInTheDocument();
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
