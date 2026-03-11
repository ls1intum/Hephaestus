import { describe, expect, it } from "vitest";
import type { GitLabGroup, GitLabPreflightResponse } from "@/api/types.gen";
import { initialWizardState, type WizardState, wizardReducer } from "../wizard-context";

const validPreflight: GitLabPreflightResponse = { valid: true, username: "admin" };
const sampleGroup: GitLabGroup = {
	id: 1,
	name: "Hephaestus",
	fullPath: "ls1intum/hephaestus",
	visibility: "public",
};
const sampleGroups: GitLabGroup[] = [
	sampleGroup,
	{ id: 2, name: "Artemis", fullPath: "ls1intum/artemis" },
];

function stateAt(step: 1 | 2 | 3, overrides: Partial<WizardState> = {}): WizardState {
	const base: Record<1 | 2 | 3, Partial<WizardState>> = {
		1: {},
		2: { step: 2, preflightResult: validPreflight, groups: sampleGroups },
		3: {
			step: 3,
			preflightResult: validPreflight,
			groups: sampleGroups,
			selectedGroup: sampleGroup,
		},
	};
	return { ...initialWizardState, ...base[step], ...overrides };
}

describe("wizardReducer", () => {
	describe("SET_SERVER_URL", () => {
		it("sets the server URL and clears preflight result", () => {
			const state = stateAt(1, { preflightResult: validPreflight });
			const result = wizardReducer(state, {
				type: "SET_SERVER_URL",
				value: "https://gitlab.example.com",
			});
			expect(result.serverUrl).toBe("https://gitlab.example.com");
			expect(result.preflightResult).toBeNull();
		});
	});

	describe("SET_PAT", () => {
		it("sets the PAT and clears preflight result", () => {
			const state = stateAt(1, { preflightResult: validPreflight });
			const result = wizardReducer(state, { type: "SET_PAT", value: "glpat-new" });
			expect(result.personalAccessToken).toBe("glpat-new");
			expect(result.preflightResult).toBeNull();
		});
	});

	describe("SET_PREFLIGHT_RESULT", () => {
		it("stores the preflight result", () => {
			const result = wizardReducer(initialWizardState, {
				type: "SET_PREFLIGHT_RESULT",
				result: validPreflight,
			});
			expect(result.preflightResult).toEqual(validPreflight);
		});
	});

	describe("CLEAR_PREFLIGHT", () => {
		it("clears the preflight result", () => {
			const state = stateAt(1, { preflightResult: validPreflight });
			const result = wizardReducer(state, { type: "CLEAR_PREFLIGHT" });
			expect(result.preflightResult).toBeNull();
		});
	});

	describe("ADVANCE_TO_GROUPS", () => {
		it("advances from step 1 to step 2 with groups", () => {
			const result = wizardReducer(initialWizardState, {
				type: "ADVANCE_TO_GROUPS",
				groups: sampleGroups,
			});
			expect(result.step).toBe(2);
			expect(result.groups).toEqual(sampleGroups);
		});

		it("rejects advancement from step 2 (guard)", () => {
			const state = stateAt(2);
			const result = wizardReducer(state, { type: "ADVANCE_TO_GROUPS", groups: [] });
			expect(result).toBe(state); // unchanged reference
		});

		it("rejects advancement from step 3 (guard)", () => {
			const state = stateAt(3);
			const result = wizardReducer(state, { type: "ADVANCE_TO_GROUPS", groups: [] });
			expect(result).toBe(state);
		});
	});

	describe("SELECT_GROUP", () => {
		it("selects a group", () => {
			const state = stateAt(2);
			const result = wizardReducer(state, { type: "SELECT_GROUP", group: sampleGroup });
			expect(result.selectedGroup).toEqual(sampleGroup);
		});
	});

	describe("ADVANCE_TO_CONFIGURE", () => {
		it("advances from step 2 to step 3 when group is selected", () => {
			const state = stateAt(2, { selectedGroup: sampleGroup });
			const result = wizardReducer(state, { type: "ADVANCE_TO_CONFIGURE" });
			expect(result.step).toBe(3);
		});

		it("rejects advancement when no group is selected (guard)", () => {
			const state = stateAt(2, { selectedGroup: null });
			const result = wizardReducer(state, { type: "ADVANCE_TO_CONFIGURE" });
			expect(result).toBe(state);
		});

		it("rejects advancement from step 1 (guard)", () => {
			const state = stateAt(1, { selectedGroup: sampleGroup });
			const result = wizardReducer(state, { type: "ADVANCE_TO_CONFIGURE" });
			expect(result).toBe(state);
		});

		it("rejects advancement from step 3 (guard)", () => {
			const state = stateAt(3);
			const result = wizardReducer(state, { type: "ADVANCE_TO_CONFIGURE" });
			expect(result).toBe(state);
		});
	});

	describe("SET_DISPLAY_NAME", () => {
		it("sets the display name", () => {
			const result = wizardReducer(stateAt(3), { type: "SET_DISPLAY_NAME", value: "My WS" });
			expect(result.displayName).toBe("My WS");
		});
	});

	describe("SET_SLUG", () => {
		it("sets the slug and manual flag", () => {
			const result = wizardReducer(stateAt(3), {
				type: "SET_SLUG",
				value: "my-ws",
				manual: true,
			});
			expect(result.workspaceSlug).toBe("my-ws");
			expect(result.slugManuallyEdited).toBe(true);
		});

		it("sets auto-generated slug with manual false", () => {
			const result = wizardReducer(stateAt(3), {
				type: "SET_SLUG",
				value: "auto-slug",
				manual: false,
			});
			expect(result.workspaceSlug).toBe("auto-slug");
			expect(result.slugManuallyEdited).toBe(false);
		});
	});

	describe("GO_BACK", () => {
		it("returns same state from step 1 (no-op)", () => {
			const state = stateAt(1);
			const result = wizardReducer(state, { type: "GO_BACK" });
			expect(result).toBe(state);
		});

		it("goes from step 2 to step 1, clearing downstream state", () => {
			const state = stateAt(2, { selectedGroup: sampleGroup });
			const result = wizardReducer(state, { type: "GO_BACK" });
			expect(result.step).toBe(1);
			expect(result.groups).toEqual([]);
			expect(result.selectedGroup).toBeNull();
			expect(result.displayName).toBe("");
			expect(result.workspaceSlug).toBe("");
		});

		it("goes from step 3 to step 2, clearing step-3 state but preserving groups", () => {
			const state = stateAt(3, {
				displayName: "Test",
				workspaceSlug: "test",
				slugManuallyEdited: true,
			});
			const result = wizardReducer(state, { type: "GO_BACK" });
			expect(result.step).toBe(2);
			expect(result.displayName).toBe("");
			expect(result.workspaceSlug).toBe("");
			expect(result.slugManuallyEdited).toBe(false);
			// Groups and selectedGroup preserved
			expect(result.groups).toEqual(sampleGroups);
			expect(result.selectedGroup).toEqual(sampleGroup);
		});
	});

	describe("RESET", () => {
		it("returns to initial state", () => {
			const state = stateAt(3, {
				serverUrl: "https://gitlab.example.com",
				personalAccessToken: "token",
				displayName: "Test",
				workspaceSlug: "test",
			});
			const result = wizardReducer(state, { type: "RESET" });
			expect(result).toEqual(initialWizardState);
		});
	});
});
