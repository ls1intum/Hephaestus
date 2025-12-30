import { describe, expect, it } from "vitest";
import {
	extractWorkspaceSlug,
	isMentorPath,
	isWorkspacePath,
	replaceWorkspaceSlug,
	toWorkspacePath,
} from "./workspace-paths";

describe("toWorkspacePath", () => {
	it("generates base workspace path", () => {
		expect(toWorkspacePath("my-workspace")).toBe("/w/my-workspace");
	});

	it("generates path with subpath", () => {
		expect(toWorkspacePath("my-workspace", "mentor")).toBe("/w/my-workspace/mentor");
	});

	it("generates nested subpaths", () => {
		expect(toWorkspacePath("my-workspace", "user/john")).toBe("/w/my-workspace/user/john");
		expect(toWorkspacePath("my-workspace", "admin/settings")).toBe(
			"/w/my-workspace/admin/settings",
		);
	});

	it("normalizes subpath with leading slash", () => {
		expect(toWorkspacePath("my-workspace", "/mentor")).toBe("/w/my-workspace/mentor");
	});

	it("normalizes subpath with trailing slash", () => {
		expect(toWorkspacePath("my-workspace", "mentor/")).toBe("/w/my-workspace/mentor");
	});

	it("handles empty subpath", () => {
		expect(toWorkspacePath("my-workspace", "")).toBe("/w/my-workspace");
	});

	it("throws for empty workspaceSlug", () => {
		expect(() => toWorkspacePath("")).toThrow("workspaceSlug is required");
	});

	it("handles mentor thread paths", () => {
		expect(toWorkspacePath("my-workspace", "mentor/abc-123")).toBe(
			"/w/my-workspace/mentor/abc-123",
		);
	});

	it("handles best practices paths", () => {
		expect(toWorkspacePath("my-workspace", "user/john/best-practices")).toBe(
			"/w/my-workspace/user/john/best-practices",
		);
	});
});

describe("isWorkspacePath", () => {
	it("returns true for workspace paths", () => {
		expect(isWorkspacePath("/w/my-workspace")).toBe(true);
		expect(isWorkspacePath("/w/my-workspace/mentor")).toBe(true);
		expect(isWorkspacePath("/w/test/admin/settings")).toBe(true);
	});

	it("returns false for non-workspace paths", () => {
		expect(isWorkspacePath("/")).toBe(false);
		expect(isWorkspacePath("/landing")).toBe(false);
		expect(isWorkspacePath("/settings")).toBe(false);
		expect(isWorkspacePath("/about")).toBe(false);
	});
});

describe("extractWorkspaceSlug", () => {
	it("extracts slug from workspace path", () => {
		expect(extractWorkspaceSlug("/w/my-workspace")).toBe("my-workspace");
		expect(extractWorkspaceSlug("/w/my-workspace/mentor")).toBe("my-workspace");
		expect(extractWorkspaceSlug("/w/test-123/admin/settings")).toBe("test-123");
	});

	it("returns undefined for non-workspace paths", () => {
		expect(extractWorkspaceSlug("/")).toBe(undefined);
		expect(extractWorkspaceSlug("/settings")).toBe(undefined);
		expect(extractWorkspaceSlug("/mentor")).toBe(undefined);
	});
});

describe("isMentorPath", () => {
	it("returns true for mentor paths", () => {
		expect(isMentorPath("/w/my-workspace/mentor")).toBe(true);
		expect(isMentorPath("/w/my-workspace/mentor/thread-123")).toBe(true);
	});

	it("returns false for non-mentor paths", () => {
		expect(isMentorPath("/w/my-workspace")).toBe(false);
		expect(isMentorPath("/w/my-workspace/teams")).toBe(false);
		expect(isMentorPath("/mentor")).toBe(false); // legacy path
	});
});

describe("replaceWorkspaceSlug", () => {
	it("replaces slug in workspace path", () => {
		expect(replaceWorkspaceSlug("/w/old-workspace/mentor", "new-workspace")).toBe(
			"/w/new-workspace/mentor",
		);
	});

	it("preserves subpath when replacing", () => {
		expect(replaceWorkspaceSlug("/w/old/admin/settings", "new")).toBe("/w/new/admin/settings");
	});

	it("handles base workspace path", () => {
		expect(replaceWorkspaceSlug("/w/old", "new")).toBe("/w/new");
	});

	it("returns new workspace path for non-workspace paths", () => {
		expect(replaceWorkspaceSlug("/settings", "new")).toBe("/w/new");
		expect(replaceWorkspaceSlug("/", "new")).toBe("/w/new");
	});
});
