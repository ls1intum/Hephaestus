import { describe, expect, it } from "vitest";
import type { ConfigAuditEntryView } from "@/api/types.gen";
import {
	actionLabel,
	actorDisplay,
	changeSummary,
	entityTypeLabel,
	fieldChanges,
	formatLeaf,
	subjectLabel,
} from "./configAuditFormat";

function entry(over: Partial<ConfigAuditEntryView>): ConfigAuditEntryView {
	return {
		id: 1,
		occurredAt: new Date("2026-07-10T10:00:00Z"),
		actorKind: "USER",
		action: "UPDATED",
		entityType: "PRACTICE_REVIEW_SETTINGS",
		entityId: "5",
		changedKeys: [],
		...over,
	};
}

describe("entityTypeLabel / actionLabel", () => {
	it("maps known types and actions to human labels", () => {
		expect(entityTypeLabel("AGENT_CONFIG")).toBe("Agent config");
		expect(actionLabel("CREATED")).toBe("Created");
	});
	it("falls back to the raw value for anything unknown", () => {
		expect(entityTypeLabel("FUTURE_TYPE")).toBe("FUTURE_TYPE");
		expect(actionLabel(undefined)).toBe("—");
	});
});

describe("fieldChanges", () => {
	it("resolves before/after leaves for each changed key", () => {
		const changes = fieldChanges(
			entry({
				changedKeys: ["cooldownMinutes"],
				oldValue: '{"cooldownMinutes":30,"skipDrafts":true}',
				newValue: '{"cooldownMinutes":10,"skipDrafts":true}',
			}),
		);
		expect(changes).toEqual([{ path: "cooldownMinutes", before: "30", after: "10" }]);
	});

	it("renders a cleared override as 'not set', not an absent field", () => {
		// The single most valuable row: admin cleared cooldown back to inherit.
		const [change] = fieldChanges(
			entry({
				changedKeys: ["cooldownMinutes"],
				oldValue: '{"cooldownMinutes":30}',
				newValue: '{"cooldownMinutes":null}',
			}),
		);
		expect(change).toEqual({ path: "cooldownMinutes", before: "30", after: "not set" });
	});

	it("resolves nested dot-paths to the leaf, not the container", () => {
		const [change] = fieldChanges(
			entry({
				changedKeys: ["volumeCaps.perPullRequest"],
				oldValue: '{"volumeCaps":{"perPullRequest":5}}',
				newValue: '{"volumeCaps":{"perPullRequest":3}}',
			}),
		);
		expect(change).toEqual({ path: "volumeCaps.perPullRequest", before: "5", after: "3" });
	});

	it("masks a credential boolean end-to-end through fieldChanges, not just in formatLeaf isolation", () => {
		// Pins the real bug: fieldChanges must pass the key path to formatLeaf, or llmApiKeySet renders
		// "false → true". A false-positive guard rides along: publicKey must NOT be masked.
		expect(
			fieldChanges(
				entry({
					entityType: "AGENT_CONFIG",
					changedKeys: ["llmApiKeySet"],
					oldValue: '{"llmApiKeySet":false}',
					newValue: '{"llmApiKeySet":true}',
				}),
			),
		).toEqual([{ path: "llmApiKeySet", before: "not set", after: "••••••" }]);
		expect(
			fieldChanges(
				entry({
					changedKeys: ["publicKey"],
					oldValue: '{"publicKey":false}',
					newValue: '{"publicKey":true}',
				}),
			),
		).toEqual([{ path: "publicKey", before: "false", after: "true" }]);
	});

	it("has no before side for a CREATED row", () => {
		const changes = fieldChanges(
			entry({
				action: "CREATED",
				changedKeys: ["name", "enabled"],
				oldValue: undefined,
				newValue: '{"name":"Primary","enabled":true}',
			}),
		);
		expect(changes.every((c) => c.before === null)).toBe(true);
		expect(changes.map((c) => c.path).sort()).toEqual(["enabled", "name"]);
	});

	it("has no after side for a DELETED row", () => {
		const changes = fieldChanges(
			entry({
				action: "DELETED",
				changedKeys: ["name"],
				oldValue: '{"name":"Primary"}',
				newValue: undefined,
			}),
		);
		expect(changes.every((c) => c.after === null)).toBe(true);
	});
});

describe("formatLeaf", () => {
	it("masks a credential-shaped boolean so it is never mistaken for the secret", () => {
		expect(formatLeaf(true, "llmApiKeySet")).toBe("••••••");
		expect(formatLeaf(false, "llmApiKeySet")).toBe("not set");
	});
	it("renders ordinary scalars literally", () => {
		expect(formatLeaf(true, "enabled")).toBe("true");
		expect(formatLeaf(42, "timeoutSeconds")).toBe("42");
		expect(formatLeaf("gpt-5", "modelName")).toBe("gpt-5");
	});
	it("renders null/undefined as 'not set'", () => {
		expect(formatLeaf(null)).toBe("not set");
		expect(formatLeaf(undefined)).toBe("not set");
	});
});

describe("changeSummary", () => {
	it("names created/deleted without a diff arrow", () => {
		expect(changeSummary(entry({ action: "CREATED", newValue: "{}" }))).toBe("Created");
		expect(changeSummary(entry({ action: "DELETED", oldValue: "{}" }))).toBe("Deleted");
	});
	it("spells out one or two field changes inline", () => {
		expect(
			changeSummary(
				entry({
					changedKeys: ["cooldownMinutes"],
					oldValue: '{"cooldownMinutes":30}',
					newValue: '{"cooldownMinutes":10}',
				}),
			),
		).toBe("cooldownMinutes: 30 → 10");
	});
	it("collapses many field changes to a count + key list", () => {
		expect(
			changeSummary(
				entry({
					changedKeys: ["a", "b", "c"],
					oldValue: '{"a":1,"b":1,"c":1}',
					newValue: '{"a":2,"b":2,"c":2}',
				}),
			),
		).toBe("3 settings changed: a, b, c");
	});
});

describe("subjectLabel", () => {
	it("enriches the subject with a name from the snapshot when present", () => {
		expect(
			subjectLabel(
				entry({
					entityType: "AGENT_CONFIG",
					entityId: "42",
					newValue: '{"name":"GPT-5 reviewer"}',
				}),
			),
		).toEqual({ label: 'Agent config "GPT-5 reviewer"', hint: "Agent config #42" });
	});
	it("falls back to type + identifier without inventing a name", () => {
		expect(
			subjectLabel(entry({ entityType: "AGENT_CONFIG", entityId: "42", newValue: "{}" })),
		).toEqual({
			label: "Agent config #42",
		});
	});
	it("renders a slug identifier as-is", () => {
		expect(
			subjectLabel(
				entry({ entityType: "AI_CONFIG_BINDING", entityId: "practice-config", newValue: "{}" }),
			),
		).toEqual({ label: "AI binding practice-config" });
	});
});

describe("actorDisplay", () => {
	it("shows a signed-in user by name", () => {
		expect(
			actorDisplay(
				entry({ actorKind: "USER", actorAccountId: 7, actor: { id: 7, displayName: "Grace" } }),
			),
		).toMatchObject({ kind: "USER", primary: "Grace" });
	});
	it("labels a background actor 'System'", () => {
		expect(actorDisplay(entry({ actorKind: "SYSTEM", actor: undefined }))).toEqual({
			kind: "SYSTEM",
			primary: "System",
		});
	});
	it("attributes impersonation to the operator, showing the assumed identity", () => {
		expect(
			actorDisplay(
				entry({
					actorKind: "IMPERSONATED",
					actorAccountId: 42,
					actor: { id: 42, displayName: "Ada" },
					actingAccountId: 7,
					actingActor: { id: 7, displayName: "Grace" },
				}),
			),
		).toMatchObject({ kind: "IMPERSONATED", primary: "Grace", actingAs: "Ada" });
	});
});
