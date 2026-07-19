import type { ConfigAuditActorRef, ConfigAuditEntryView } from "@/api/types.gen";

/**
 * Formatting for the "Configuration changes" audit surface. The product's value is the diff — *who
 * changed which setting, from what to what* — so these helpers turn the server's `changedKeys` +
 * `oldValue`/`newValue` snapshots into human before/after pairs, never raw JSON. LaunchDarkly's change
 * history, Stripe's `previous_attributes` and Datadog's audit trail all render field-level diffs rather
 * than blobs; this is that, driven by the changed-key list the server already computed.
 */

type EntityType = NonNullable<ConfigAuditEntryView["entityType"]>;
type Action = NonNullable<ConfigAuditEntryView["action"]>;
type ActorKind = NonNullable<ConfigAuditEntryView["actorKind"]>;

/** Human label for the kind of resource a row is about. A map, not string-munging, so it stays honest. */
const ENTITY_TYPE_LABELS: Record<EntityType, string> = {
	PRACTICE_REVIEW_SETTINGS: "Review settings",
	AI_CONFIG_BINDING: "AI binding",
	AGENT_CONFIG: "Agent config",
};

export function entityTypeLabel(entityType: string | undefined): string {
	return entityType && entityType in ENTITY_TYPE_LABELS
		? ENTITY_TYPE_LABELS[entityType as EntityType]
		: (entityType ?? "Unknown");
}

const ACTION_LABELS: Record<Action, string> = {
	CREATED: "Created",
	UPDATED: "Updated",
	DELETED: "Deleted",
};

export function actionLabel(action: string | undefined): string {
	return action && action in ACTION_LABELS ? ACTION_LABELS[action as Action] : (action ?? "—");
}

/**
 * The affected resource as a short, honest subject. Enriched from the snapshot the API already returned:
 * if the new/old value carries a `name`, show it (immutable — stays correct after the resource is
 * deleted); otherwise fall back to the type + identifier. No extra requests, no invented data.
 */
export function subjectLabel(entry: ConfigAuditEntryView): { label: string; hint?: string } {
	const type = entityTypeLabel(entry.entityType);
	const snapshot = parseSnapshot(entry.newValue) ?? parseSnapshot(entry.oldValue);
	const name = snapshot && typeof snapshot.name === "string" ? snapshot.name : undefined;
	const id = entry.entityId;
	if (name) {
		return {
			label: `${type} "${name}"`,
			hint: id ? `${entry.entityType} ${identifier(id)}` : undefined,
		};
	}
	return { label: id ? `${type} ${identifier(id)}` : type };
}

/** Slugs render as-is (already human), numeric ids as `#42`. */
function identifier(entityId: string): string {
	return /^\d+$/.test(entityId) ? `#${entityId}` : entityId;
}

/** A resolved actor as a human label; `#id` for a purged account so the row stays attributable. */
export function actorLabel(
	ref: ConfigAuditActorRef | undefined,
	id: number | undefined,
): string | null {
	if (ref) return ref.displayName || ref.email || `#${ref.id}`;
	if (id != null) return `#${id}`;
	return null;
}

export interface ActorDisplay {
	kind: ActorKind;
	/** The responsible party: the signed-in user, the impersonating operator, or "System". */
	primary: string;
	primaryEmail?: string;
	/** For impersonation: the identity acted as. */
	actingAs?: string;
}

/**
 * Who caused the change. `SYSTEM` is a background job (seeder, scheduler); `IMPERSONATED` attributes the
 * change to the operator with the assumed identity shown as "acting as …" — the established audit
 * convention (Microsoft 365: "on behalf of the user"). Note the field mapping: `actingActor` is the
 * impersonating operator, `actor` is the identity they assumed.
 */
export function actorDisplay(entry: ConfigAuditEntryView): ActorDisplay {
	const kind = entry.actorKind ?? "SYSTEM";
	if (kind === "SYSTEM") {
		return { kind, primary: "System" };
	}
	if (kind === "IMPERSONATED") {
		const operator = actorLabel(entry.actingActor, entry.actingAccountId) ?? "Unknown operator";
		const assumed = actorLabel(entry.actor, entry.actorAccountId);
		return {
			kind,
			primary: operator,
			primaryEmail: entry.actingActor?.email,
			actingAs: assumed ?? undefined,
		};
	}
	return {
		kind,
		primary: actorLabel(entry.actor, entry.actorAccountId) ?? "Unknown",
		primaryEmail: entry.actor?.email,
	};
}

export interface FieldChange {
	/** The dot-path of the changed field, e.g. `cooldownMinutes` or `volumeCaps.perPullRequest`. */
	path: string;
	/** Human-rendered prior value; `null` for a CREATED row (no prior state). */
	before: string | null;
	/** Human-rendered new value; `null` for a DELETED row. */
	after: string | null;
}

/**
 * The field-level diff: one entry per `changedKeys` path, resolving the leaf value out of the parsed
 * old/new snapshots. This is the spine of the UI — the row summary and the detail sheet both render
 * from it, so there is one definition of "what changed".
 */
export function fieldChanges(entry: ConfigAuditEntryView): FieldChange[] {
	const before = parseSnapshot(entry.oldValue);
	const after = parseSnapshot(entry.newValue);
	const keys =
		entry.changedKeys && entry.changedKeys.length > 0
			? entry.changedKeys
			: // Defensive: a CREATED/DELETED row may not carry changedKeys; fall back to the present side's leaves.
				Object.keys(flatten(after ?? before ?? {}));
	return keys.map((path) => ({
		path,
		before: before ? formatLeaf(leafAt(before, path)) : null,
		after: after ? formatLeaf(leafAt(after, path)) : null,
	}));
}

/**
 * A single leaf value as display text. Credentials never arrive here (the server redacts them to a
 * `*Set` boolean), but a `*Set`/`*Key` boolean is rendered as a masked marker so a reader never mistakes
 * the boolean for the secret itself.
 */
export function formatLeaf(value: unknown, path?: string): string {
	if (value === undefined) return "not set";
	if (value === null) return "not set";
	if (typeof value === "boolean" && path && /key|secret|token|password/i.test(path)) {
		return value ? "••••••" : "not set";
	}
	if (typeof value === "boolean") return String(value);
	if (typeof value === "number") return String(value);
	if (typeof value === "string") return value.length === 0 ? '""' : value;
	if (Array.isArray(value)) return JSON.stringify(value);
	return JSON.stringify(value);
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

/** Pretty-print a raw snapshot for the drawer's collapsible "raw" section; raw string if not JSON. */
export function prettySnapshot(value: string | undefined): string | null {
	if (!value) return null;
	try {
		return JSON.stringify(JSON.parse(value), null, 2);
	} catch {
		return value;
	}
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

/** Resolve a dot-path leaf out of a parsed snapshot object. */
function leafAt(obj: Record<string, unknown>, path: string): unknown {
	return path.split(".").reduce<unknown>((acc, segment) => {
		if (acc && typeof acc === "object" && !Array.isArray(acc)) {
			return (acc as Record<string, unknown>)[segment];
		}
		return undefined;
	}, obj);
}

/** Flatten nested objects to dot-paths → leaves (arrays compare whole, matching the server's diff). */
function flatten(obj: Record<string, unknown>, prefix = ""): Record<string, unknown> {
	const out: Record<string, unknown> = {};
	for (const [key, value] of Object.entries(obj)) {
		const path = prefix ? `${prefix}.${key}` : key;
		if (value && typeof value === "object" && !Array.isArray(value)) {
			Object.assign(out, flatten(value as Record<string, unknown>, path));
		} else {
			out[path] = value;
		}
	}
	return out;
}
