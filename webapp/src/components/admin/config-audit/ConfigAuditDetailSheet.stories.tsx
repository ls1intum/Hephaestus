import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import type { ConfigAuditEntryView } from "@/api/types.gen";
import { ConfigAuditDetailSheet } from "./ConfigAuditDetailSheet";

const baseEntry: ConfigAuditEntryView = {
	id: 91,
	occurredAt: "2026-07-24T09:14:32Z" as unknown as Date,
	action: "UPDATED",
	entityType: "AGENT_BINDING",
	entityId: "PRACTICE_DETECTION",
	actorKind: "USER",
	actorAccountId: 7,
	actor: { id: 7, displayName: "Ada Lovelace", email: "ada@example.com" },
	workspaceId: 3,
	changedKeys: ["timeoutSeconds", "maxConcurrentJobs"],
	oldValue: JSON.stringify({ timeoutSeconds: 600, maxConcurrentJobs: 1, allowInternet: false }),
	newValue: JSON.stringify({ timeoutSeconds: 900, maxConcurrentJobs: 3, allowInternet: false }),
};

/**
 * Full record of one configuration change: the field-by-field before/after first, then the raw
 * snapshots behind a disclosure. A right-hand Sheet keeps the change list visible behind it, which
 * is what makes "inspect this row" feel like inspecting rather than navigating away.
 */
const meta = {
	component: ConfigAuditDetailSheet,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		entry: baseEntry,
		open: true,
		onOpenChange: fn(),
		resolveWorkspaceName: (id: number) => (id === 3 ? "Acme" : undefined),
	},
} satisfies Meta<typeof ConfigAuditDetailSheet>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A settings edit by a named admin: two fields changed, both listed with old and new values. */
export const Default: Story = {
	play: async () => {
		// The sheet is portalled, so the queries run against the document rather than the canvas.
		const body = within(document.body);
		// Presence, not visibility: the sheet animates in, so a visibility assertion here races the
		// enter transition rather than testing anything about the content.
		// The workspace is named *and* numbered, so a row stays identifiable after a rename.
		await expect(await body.findByText("Acme (#3)")).toBeInTheDocument();
		await expect(await body.findByText(/timeoutSeconds/)).toBeInTheDocument();
	},
};

/**
 * A change nobody signed: `actorKind: SYSTEM` means a process did it, which must stay distinct from
 * "we no longer know who did it" (an account that has since been deleted).
 */
export const SystemActor: Story = {
	args: {
		entry: { ...baseEntry, actorKind: "SYSTEM", actor: undefined, actorAccountId: undefined },
	},
};

/** Made while impersonating: both identities are on the record, never just the effective one. */
export const Impersonated: Story = {
	args: {
		entry: {
			...baseEntry,
			actorKind: "IMPERSONATED",
			actingAccountId: 1,
			actingActor: { id: 1, displayName: "Grace Hopper", email: "grace@example.com" },
		},
	},
};

/** A creation has no "before", so the sheet shows the new snapshot without inventing an old one. */
export const Created: Story = {
	args: {
		entry: {
			...baseEntry,
			action: "CREATED",
			oldValue: undefined,
			changedKeys: undefined,
			newValue: JSON.stringify({ timeoutSeconds: 600, maxConcurrentJobs: 1 }),
		},
	},
};

/** A deletion: the last known state, and nothing after it. */
export const Deleted: Story = {
	args: {
		entry: { ...baseEntry, action: "DELETED", newValue: undefined, changedKeys: undefined },
	},
};

/** An instance-scoped change — no workspace to name. */
export const NoWorkspace: Story = {
	args: { entry: { ...baseEntry, workspaceId: undefined } },
};

/** The raw JSON snapshots are a disclosure, so the readable change list leads. */
export const RawSnapshotsRevealed: Story = {
	play: async () => {
		const body = within(document.body);
		const trigger = await body.findByRole("button", { name: "Show raw snapshots" });
		await expect(trigger).toHaveAttribute("aria-expanded", "false");
		await userEvent.click(trigger);
		await expect(trigger).toHaveAttribute("aria-expanded", "true");
	},
};

/** Closed — the sheet renders nothing. */
export const Closed: Story = {
	args: { open: false },
};
