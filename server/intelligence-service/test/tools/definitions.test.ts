/**
 * Tool Definitions Tests
 *
 * Ensures all mentor tool definitions follow Langfuse config best practices.
 * Definitions are now colocated with each tool file (*.tool.ts).
 */

import { describe, expect, it } from "vitest";
import {
	createDocumentDefinition,
	getActivitySummaryDefinition,
	getAssignedWorkDefinition,
	getDocumentsDefinition,
	getFeedbackReceivedDefinition,
	getIssuesDefinition,
	getPullRequestsDefinition,
	getReviewsGivenDefinition,
	getSessionHistoryDefinition,
	mentorToolDefinitions,
	updateDocumentDefinition,
} from "@/mentor/tools";
import type { PromptToolDefinition } from "@/prompts/types";

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function validateToolDefinition(def: PromptToolDefinition) {
	// Must have type: "function"
	expect(def.type).toBe("function");

	// Must have function object
	expect(def.function).toBeDefined();
	expect(typeof def.function.name).toBe("string");
	expect(def.function.name.length).toBeGreaterThan(0);

	// Must have description
	const description = def.function.description;
	expect(typeof description).toBe("string");
	expect(description?.length).toBeGreaterThan(10);

	// Must have parameters with JSON Schema structure
	expect(def.function.parameters).toBeDefined();
	expect(def.function.parameters.type).toBe("object");
	expect(def.function.parameters.properties).toBeDefined();
	expect(Array.isArray(def.function.parameters.required)).toBe(true);
	expect(def.function.parameters.additionalProperties).toBe(false);
}

// ─────────────────────────────────────────────────────────────────────────────
// Test: All Definitions Structure
// ─────────────────────────────────────────────────────────────────────────────

describe("mentorToolDefinitions", () => {
	it("should export an array of tool definitions", () => {
		expect(Array.isArray(mentorToolDefinitions)).toBe(true);
		expect(mentorToolDefinitions.length).toBeGreaterThan(0);
	});

	it("should have 10 tool definitions", () => {
		// 8 activity tools + 2 document tools
		expect(mentorToolDefinitions.length).toBe(10);
	});

	it("should have all definitions follow Langfuse schema", () => {
		for (const def of mentorToolDefinitions) {
			validateToolDefinition(def);
		}
	});

	it("should have unique tool names", () => {
		const names = mentorToolDefinitions.map((d) => d.function.name);
		const uniqueNames = new Set(names);
		expect(uniqueNames.size).toBe(names.length);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// Test: Individual Definitions
// ─────────────────────────────────────────────────────────────────────────────

describe("getActivitySummaryDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getActivitySummaryDefinition);
	});

	it("should have empty required parameters (no input)", () => {
		expect(getActivitySummaryDefinition.function.parameters.required).toEqual([]);
	});

	it("should have description mentioning when to use", () => {
		expect(getActivitySummaryDefinition.function.description).toContain("When to use");
		expect(getActivitySummaryDefinition.function.description).toContain("CRITICAL");
	});
});

describe("getPullRequestsDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getPullRequestsDefinition);
	});

	it("should require state, limit, and sinceDays parameters", () => {
		expect(getPullRequestsDefinition.function.parameters.required).toContain("state");
		expect(getPullRequestsDefinition.function.parameters.required).toContain("limit");
		expect(getPullRequestsDefinition.function.parameters.required).toContain("sinceDays");
	});

	it("should have state enum", () => {
		const properties = getPullRequestsDefinition.function.parameters.properties as Record<
			string,
			{ enum?: string[] }
		>;
		const stateParam = properties.state;
		expect(stateParam?.enum).toEqual(["open", "merged", "closed", "all"]);
	});
});

describe("getIssuesDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getIssuesDefinition);
	});

	it("should require state and limit parameters", () => {
		expect(getIssuesDefinition.function.parameters.required).toContain("state");
		expect(getIssuesDefinition.function.parameters.required).toContain("limit");
	});
});

describe("getAssignedWorkDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getAssignedWorkDefinition);
	});

	it("should have description mentioning Zimmerman Forethought", () => {
		expect(getAssignedWorkDefinition.function.description).toContain("Zimmerman Forethought Phase");
	});
});

describe("getFeedbackReceivedDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getFeedbackReceivedDefinition);
	});

	it("should require sinceDays and includeThreads", () => {
		expect(getFeedbackReceivedDefinition.function.parameters.required).toContain("sinceDays");
		expect(getFeedbackReceivedDefinition.function.parameters.required).toContain("includeThreads");
	});
});

describe("getReviewsGivenDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getReviewsGivenDefinition);
	});

	it("should require sinceDays and limit", () => {
		expect(getReviewsGivenDefinition.function.parameters.required).toContain("sinceDays");
		expect(getReviewsGivenDefinition.function.parameters.required).toContain("limit");
	});
});

describe("getSessionHistoryDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getSessionHistoryDefinition);
	});

	it("should require limit parameter", () => {
		expect(getSessionHistoryDefinition.function.parameters.required).toContain("limit");
	});
});

describe("getDocumentsDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(getDocumentsDefinition);
	});

	it("should require limit parameter", () => {
		expect(getDocumentsDefinition.function.parameters.required).toContain("limit");
	});
});

describe("createDocumentDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(createDocumentDefinition);
	});

	it("should require title and kind", () => {
		expect(createDocumentDefinition.function.parameters.required).toContain("title");
		expect(createDocumentDefinition.function.parameters.required).toContain("kind");
	});

	it("should have kind enum", () => {
		const properties = createDocumentDefinition.function.parameters.properties as Record<
			string,
			{ enum?: string[] }
		>;
		const kindParam = properties.kind;
		expect(kindParam?.enum).toBeDefined();
	});
});

describe("updateDocumentDefinition", () => {
	it("should have correct structure", () => {
		validateToolDefinition(updateDocumentDefinition);
	});

	it("should require id and description", () => {
		expect(updateDocumentDefinition.function.parameters.required).toContain("id");
		expect(updateDocumentDefinition.function.parameters.required).toContain("description");
	});
});
