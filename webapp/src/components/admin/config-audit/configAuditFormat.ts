import type { ConfigAuditEntryView } from "@/api/types.gen";
import { refLabel } from "../audit-shared/refLabel";

/**
 * Formatting for the config-audit surface: turning the server's `changedKeys` + `oldValue`/`newValue`
 * snapshots into human before/after pairs. See `ConfigAuditDiff` (server) for how the diff is computed;
 * this is the read side.
 */

type EntityType = NonNullable<ConfigAuditEntryView["entityType"]>;
type Action = NonNullable<ConfigAuditEntryView["action"]>;
type ActorKind = NonNullable<ConfigAuditEntryView["actorKind"]>;

export const ENTITY_TYPE_LABELS: Record<EntityType, string> = {
	PRACTICE_REVIEW_SETTINGS: "Review settings",
	AI_CONFIG_BINDING: "AI binding",
	AGENT_CONFIG: "Agent config",
	WORKSPACE_ROLE: "Member role",
	WORKSPACE_FEATURES: "Feature flags",
	WORKSPACE_STATUS: "Workspace status",
	WORKSPACE_TOKEN: "Access token",
	WORKSPACE_VISIBILITY: "Visibility",
};

export const ACTION_LABELS: Record<Action, string> = {
	CREATED: "Created",
	UPDATED: "Updated",
	DELETED: "Deleted",
};

/** Badge variant per action, so the table and the sheet stay in sync. */
export const ACTION_BADGE: Record<Action, "default" | "secondary" | "outline"> = {
	CREATED: "default",
	UPDATED: "secondary",
	DELETED: "outline",
};

export function entityTypeLabel(entityType: string | undefined): string {
	return entityType && entityType in ENTITY_TYPE_LABELS
		? ENTITY_TYPE_LABELS[entityType as EntityType]
		: (entityType ?? "Unknown");
}

export function actionLabel(action: string | undefined): string {
	return action && action in ACTION_LABELS ? ACTION_LABELS[action as Action] : (action ?? "—");
}

export interface ActorDisplay {
	kind: ActorKind;
	/** The responsible party: the signed-in user, the impersonating operator, or "System". */
	primary: string;
	primaryEmail?: string;
	/** For impersonation: the identity acted as. */
	actingAs?: string;
	/** The account id to filter by (the operator for an impersonated change), if any. */
	filterId?: number;
}

/**
 * Who caused the change. `actingActor` is the impersonating operator; `actor` is the identity they
 * assumed — mixing them up would misattribute the change, so the mapping is asserted in the tests.
 */
export function actorDisplay(entry: ConfigAuditEntryView): ActorDisplay {
	const kind = entry.actorKind ?? "SYSTEM";
	if (kind === "SYSTEM") {
		return { kind, primary: "System" };
	}
	if (kind === "IMPERSONATED") {
		return {
			kind,
			primary: refLabel(entry.actingActor, entry.actingAccountId) ?? "Unknown operator",
			primaryEmail: entry.actingActor?.email,
			actingAs: refLabel(entry.actor, entry.actorAccountId) ?? undefined,
			filterId: entry.actingAccountId,
		};
	}
	return {
		kind,
		primary: refLabel(entry.actor, entry.actorAccountId) ?? "Unknown",
		primaryEmail: entry.actor?.email,
		filterId: entry.actorAccountId,
	};
}

export interface FieldChange {
	/** The dot-path of the changed field, e.g. `cooldownMinutes` or `volumeCaps.perPullRequest`. */
	path: string;
	/** Prior value; `null` for a CREATED row (no prior state). */
	before: string | null;
	/** New value; `null` for a DELETED row. */
	after: string | null;
}

/**
 * A single leaf value as display text. Credentials arrive as a `…Set` boolean (the server redacts them,
 * enforced by `ConfigAuditSnapshotArchTest`), rendered masked so a reader never reads the boolean as the
 * secret. The suffix anchor keeps ordinary keys like `publicKey` from matching.
 */
export function formatLeaf(value: unknown, path?: string): string {
	if (value === undefined || value === null) return "not set";
	if (typeof value === "boolean" && path && /(key|secret|token|password)set$/i.test(path)) {
		return value ? "••••••" : "not set";
	}
	if (typeof value === "boolean" || typeof value === "number") return String(value);
	if (typeof value === "string") return value.length === 0 ? '""' : value;
	return JSON.stringify(value);
}

/** The field-level diff: one entry per changed path, resolved from the parsed snapshots. */
export function fieldChanges(entry: ConfigAuditEntryView): FieldChange[] {
	const before = parseSnapshot(entry.oldValue);
	const after = parseSnapshot(entry.newValue);
	return (entry.changedKeys ?? []).map((path) => ({
		path,
		before: before ? formatLeaf(leafAt(before, path), path) : null,
		after: after ? formatLeaf(leafAt(after, path), path) : null,
	}));
}

/** The affected resource as a short subject, enriched with the snapshot's `name` when present. */
export function subjectLabel(entry: ConfigAuditEntryView): { label: string; hint?: string } {
	const type = entityTypeLabel(entry.entityType);
	const snapshot = parseSnapshot(entry.newValue) ?? parseSnapshot(entry.oldValue);
	const name =
		snapshot && typeof snapshot.name === "string" && snapshot.name ? snapshot.name : undefined;
	const id = entry.entityId;
	if (name) {
		return { label: `${type} "${name}"`, hint: id ? `${type} ${identifier(id)}` : undefined };
	}
	return { label: id ? `${type} ${identifier(id)}` : type };
}

/** A one-line summary of the change for the table row. */
export function changeSummary(entry: ConfigAuditEntryView): string {
	if (entry.action === "CREATED") return "Created";
	if (entry.action === "DELETED") return "Deleted";
	const changes = fieldChanges(entry);
	if (changes.length === 0) return "No field changes";
	if (changes.length <= 2) {
		return changes.map((c) => `${c.path}: ${c.before ?? "—"} → ${c.after ?? "—"}`).join(" · ");
	}
	return `${changes.length} settings changed: ${changes.map((c) => c.path).join(", ")}`;
}

/** Slugs render as-is (already human), numeric ids as `#42`. */
function identifier(entityId: string): string {
	return /^\d+$/.test(entityId) ? `#${entityId}` : entityId;
}

function parseSnapshot(value: string | undefined): Record<string, unknown> | null {
	if (!value) return null;
	try {
		const parsed = JSON.parse(value);
		return parsed && typeof parsed === "object" && !Array.isArray(parsed)
			? (parsed as Record<string, unknown>)
			: null;
	} catch {
		return null;
	}
}

/** Resolve a dot-path leaf out of a parsed snapshot object; arrays compare whole (never index into them). */
function leafAt(obj: Record<string, unknown>, path: string): unknown {
	return path.split(".").reduce<unknown>((acc, segment) => {
		if (acc && typeof acc === "object" && !Array.isArray(acc)) {
			return (acc as Record<string, unknown>)[segment];
		}
		return undefined;
	}, obj);
}
