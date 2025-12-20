/**
 * Chat Shared Module Tests
 *
 * Tests the exported type guards, parsers, and schemas from chat.shared.ts.
 * These are used by both the server and webapp for type-safe tool handling.
 */

import { describe, expect, it } from "vitest";
import {
	createDocumentInputSchema,
	createDocumentOutputSchema,
	hasDocumentId,
	parseCreateDocumentInput,
	parseCreateDocumentOutput,
	parseUpdateDocumentInput,
	parseUpdateDocumentOutput,
	updateDocumentInputSchema,
	updateDocumentOutputSchema,
} from "@/mentor/chat/chat.shared";

describe("Chat Shared Module", () => {
	// ─────────────────────────────────────────────────────────────────────────
	// Input Schema Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("createDocumentInputSchema", () => {
		it("should accept valid input", () => {
			const input = { title: "My Document", kind: "text" };
			const result = createDocumentInputSchema.safeParse(input);
			expect(result.success).toBe(true);
			if (result.success) {
				expect(result.data.title).toBe("My Document");
				expect(result.data.kind).toBe("text");
			}
		});

		it("should reject empty title", () => {
			const input = { title: "", kind: "text" };
			const result = createDocumentInputSchema.safeParse(input);
			expect(result.success).toBe(false);
		});

		it("should reject title over 255 characters", () => {
			const input = { title: "a".repeat(256), kind: "text" };
			const result = createDocumentInputSchema.safeParse(input);
			expect(result.success).toBe(false);
		});

		it("should reject invalid kind", () => {
			const input = { title: "Doc", kind: "invalid" };
			const result = createDocumentInputSchema.safeParse(input);
			expect(result.success).toBe(false);
		});

		it("should reject missing fields", () => {
			expect(createDocumentInputSchema.safeParse({}).success).toBe(false);
			expect(createDocumentInputSchema.safeParse({ title: "Doc" }).success).toBe(false);
			expect(createDocumentInputSchema.safeParse({ kind: "text" }).success).toBe(false);
		});
	});

	describe("updateDocumentInputSchema", () => {
		it("should accept valid input", () => {
			const input = {
				id: "123e4567-e89b-12d3-a456-426614174000",
				description: "Update the formatting",
			};
			const result = updateDocumentInputSchema.safeParse(input);
			expect(result.success).toBe(true);
		});

		it("should reject invalid UUID", () => {
			const input = { id: "not-a-uuid", description: "Update" };
			const result = updateDocumentInputSchema.safeParse(input);
			expect(result.success).toBe(false);
		});

		it("should reject missing fields", () => {
			expect(updateDocumentInputSchema.safeParse({}).success).toBe(false);
			expect(
				updateDocumentInputSchema.safeParse({
					id: "123e4567-e89b-12d3-a456-426614174000",
				}).success,
			).toBe(false);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Output Schema Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("createDocumentOutputSchema", () => {
		it("should accept valid output", () => {
			const output = {
				id: "doc-123",
				title: "My Document",
				kind: "text",
				content: "Hello world",
			};
			const result = createDocumentOutputSchema.safeParse(output);
			expect(result.success).toBe(true);
		});

		it("should reject missing fields", () => {
			const output = { id: "doc-123", title: "My Document" };
			const result = createDocumentOutputSchema.safeParse(output);
			expect(result.success).toBe(false);
		});

		it("should reject invalid kind", () => {
			const output = {
				id: "doc-123",
				title: "My Document",
				kind: "image",
				content: "",
			};
			const result = createDocumentOutputSchema.safeParse(output);
			expect(result.success).toBe(false);
		});
	});

	describe("updateDocumentOutputSchema", () => {
		it("should accept valid output", () => {
			const output = {
				id: "doc-123",
				title: "My Document",
				kind: "text",
				content: "Updated content",
				description: "Fixed typos",
			};
			const result = updateDocumentOutputSchema.safeParse(output);
			expect(result.success).toBe(true);
		});

		it("should reject missing description", () => {
			const output = {
				id: "doc-123",
				title: "My Document",
				kind: "text",
				content: "Updated content",
			};
			const result = updateDocumentOutputSchema.safeParse(output);
			expect(result.success).toBe(false);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Parser Function Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("parseCreateDocumentInput", () => {
		it("should return typed input for valid data", () => {
			const input = { title: "My Document", kind: "text" };
			const result = parseCreateDocumentInput(input);
			expect(result).toBeDefined();
			expect(result?.title).toBe("My Document");
			expect(result?.kind).toBe("text");
		});

		it("should return undefined for invalid data", () => {
			expect(parseCreateDocumentInput(null)).toBeUndefined();
			expect(parseCreateDocumentInput(undefined)).toBeUndefined();
			expect(parseCreateDocumentInput({})).toBeUndefined();
			expect(parseCreateDocumentInput({ title: "" })).toBeUndefined();
			expect(parseCreateDocumentInput("string")).toBeUndefined();
			expect(parseCreateDocumentInput(123)).toBeUndefined();
		});
	});

	describe("parseCreateDocumentOutput", () => {
		it("should return typed output for valid data", () => {
			const output = {
				id: "doc-123",
				title: "My Document",
				kind: "text",
				content: "Hello world",
			};
			const result = parseCreateDocumentOutput(output);
			expect(result).toBeDefined();
			expect(result?.id).toBe("doc-123");
		});

		it("should return undefined for invalid data", () => {
			expect(parseCreateDocumentOutput(null)).toBeUndefined();
			expect(parseCreateDocumentOutput(undefined)).toBeUndefined();
			expect(parseCreateDocumentOutput({})).toBeUndefined();
		});
	});

	describe("parseUpdateDocumentInput", () => {
		it("should return typed input for valid data", () => {
			const input = {
				id: "123e4567-e89b-12d3-a456-426614174000",
				description: "Update text",
			};
			const result = parseUpdateDocumentInput(input);
			expect(result).toBeDefined();
			expect(result?.id).toBe("123e4567-e89b-12d3-a456-426614174000");
			expect(result?.description).toBe("Update text");
		});

		it("should return undefined for invalid UUID", () => {
			const input = { id: "invalid-uuid", description: "Update" };
			expect(parseUpdateDocumentInput(input)).toBeUndefined();
		});

		it("should return undefined for invalid data", () => {
			expect(parseUpdateDocumentInput(null)).toBeUndefined();
			expect(parseUpdateDocumentInput(undefined)).toBeUndefined();
			expect(parseUpdateDocumentInput({})).toBeUndefined();
		});
	});

	describe("parseUpdateDocumentOutput", () => {
		it("should return typed output for valid data", () => {
			const output = {
				id: "doc-123",
				title: "My Document",
				kind: "text",
				content: "Updated content",
				description: "Fixed typos",
			};
			const result = parseUpdateDocumentOutput(output);
			expect(result).toBeDefined();
			expect(result?.description).toBe("Fixed typos");
		});

		it("should return undefined for invalid data", () => {
			expect(parseUpdateDocumentOutput(null)).toBeUndefined();
			expect(parseUpdateDocumentOutput(undefined)).toBeUndefined();
			expect(parseUpdateDocumentOutput({})).toBeUndefined();
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Type Guard Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("hasDocumentId", () => {
		it("should return true for objects with string id", () => {
			expect(hasDocumentId({ id: "doc-123" })).toBe(true);
			expect(hasDocumentId({ id: "doc-123", other: "data" })).toBe(true);
			expect(hasDocumentId({ id: "", title: "Doc" })).toBe(true);
		});

		it("should return false for objects without id", () => {
			expect(hasDocumentId({})).toBe(false);
			expect(hasDocumentId({ title: "Doc" })).toBe(false);
		});

		it("should return false for objects with non-string id", () => {
			expect(hasDocumentId({ id: 123 })).toBe(false);
			expect(hasDocumentId({ id: null })).toBe(false);
			expect(hasDocumentId({ id: undefined })).toBe(false);
			expect(hasDocumentId({ id: {} })).toBe(false);
			expect(hasDocumentId({ id: [] })).toBe(false);
		});

		it("should return false for non-objects", () => {
			expect(hasDocumentId(null)).toBe(false);
			expect(hasDocumentId(undefined)).toBe(false);
			expect(hasDocumentId("string")).toBe(false);
			expect(hasDocumentId(123)).toBe(false);
			expect(hasDocumentId(true)).toBe(false);
			expect(hasDocumentId([])).toBe(false);
		});
	});
});
