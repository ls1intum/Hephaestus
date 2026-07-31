import type { ConfigAuditEntryView } from "@/api/types.gen";
import { refLabel } from "../audit-shared/ref-label";

type EntityType = NonNullable<ConfigAuditEntryView["entityType"]>;
type Action = NonNullable<ConfigAuditEntryView["action"]>;
type ActorKind = NonNullable<ConfigAuditEntryView["actorKind"]>;

/** Entity types renamed since: the trail is append-only, so old rows keep the spelling they were
 * written under and must still read as the thing they describe. */
const RENAMED_ENTITY_TYPE_LABELS = {
	AGENT_CONFIG: "Agent config",
	AI_CONFIG_BINDING: "AI binding",
	WORKSPACE_LLM_BUDGET: "Shared-model AI budget",
	WORKSPACE_BYO_LLM_BUDGET: "Own-provider AI cap",
} satisfies Partial<Record<EntityType, string>>;

export const ENTITY_TYPE_LABELS: Record<EntityType, string> = {
	PRACTICE_REVIEW_SETTINGS: "Review settings",
	AGENT_BINDING: "AI binding",
	WORKSPACE_ROLE: "Workspace role",
	WORKSPACE_FEATURES: "Feature flags",
	WORKSPACE_STATUS: "Workspace status",
	WORKSPACE_TOKEN: "Access token",
	WORKSPACE_VISIBILITY: "Visibility",
	PRACTICE_ACTIVE: "Practice active",
	PRACTICE_DEFINITION: "Practice",
	WORKSPACE_INSTANCE_LLM_BUDGET: "Shared-model AI budget",
	WORKSPACE_OWN_PROVIDER_LLM_BUDGET: "Own-provider AI cap",
	WORKSPACE_LLM_CONNECTION: "Workspace AI provider",
	WORKSPACE_LLM_MODEL: "Workspace model",
	...RENAMED_ENTITY_TYPE_LABELS,
};

export const ACTION_LABELS: Record<Action, string> = {
	CREATED: "Created",
	UPDATED: "Updated",
	DELETED: "Deleted",
};

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
	primary: string;
	primaryEmail?: string;
	actingAs?: string;
	filterId?: number;
}

/**
 * Who caused the change. On an impersonated row `actingActor` is the operator and `actor` the identity
 * they assumed: swapping them attributes the operator's changes to their victim.
 */
export function actorDisplay(entry: ConfigAuditEntryView): ActorDisplay {
	const kind = entry.actorKind ?? "SYSTEM";
	if (kind === "SYSTEM") {
		return { kind, primary: "System" };
	}
	if (kind === "IMPERSONATED") {
		return {
			kind,
			primary: refLabel(entry.actingActor, entry.actingAccountId) ?? "Unknown",
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
	/** Dot-path, e.g. `volumeCaps.perPullRequest`. */
	path: string;
	before: string | null;
	after: string | null;
}

/**
 * A leaf value as display text. The server redacts credentials to a `…Set` boolean, which renders
 * masked so the boolean is never read as the secret; the suffix anchor keeps `publicKey` out of it.
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

export function fieldChanges(entry: ConfigAuditEntryView): FieldChange[] {
	const before = parseSnapshot(entry.oldValue);
	const after = parseSnapshot(entry.newValue);
	return (entry.changedKeys ?? []).map((path) => ({
		path,
		before: before ? formatLeaf(leafAt(before, path), path) : null,
		after: after ? formatLeaf(leafAt(after, path), path) : null,
	}));
}

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

export function changeSummary(entry: ConfigAuditEntryView): string {
	// The row's Action badge already says Created/Deleted.
	if (entry.action === "CREATED" || entry.action === "DELETED") return "";
	const changes = fieldChanges(entry);
	if (changes.length === 0) return "";
	if (changes.length <= 2) {
		return changes.map((c) => `${c.path}: ${c.before ?? "—"} → ${c.after ?? "—"}`).join(" · ");
	}
	return `${changes.length} fields changed`;
}

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

/** Resolves a dot-path leaf; arrays are compared whole, so a segment never indexes into one. */
function leafAt(obj: Record<string, unknown>, path: string): unknown {
	return path.split(".").reduce<unknown>((acc, segment) => {
		if (acc && typeof acc === "object" && !Array.isArray(acc)) {
			return (acc as Record<string, unknown>)[segment];
		}
		return undefined;
	}, obj);
}
