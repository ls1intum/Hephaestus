import { describe, expect, it } from "vitest";
import { hasMinimumWorkspaceRole, type WorkspaceRole } from "./workspace-roles";

describe("hasMinimumWorkspaceRole", () => {
	const cases: Array<[WorkspaceRole | undefined | null, WorkspaceRole, boolean]> = [
		// OWNER meets everything
		["OWNER", "MEMBER", true],
		["OWNER", "ADMIN", true],
		["OWNER", "OWNER", true],
		// ADMIN meets ADMIN and below
		["ADMIN", "MEMBER", true],
		["ADMIN", "ADMIN", true],
		["ADMIN", "OWNER", false],
		// MEMBER only meets MEMBER
		["MEMBER", "MEMBER", true],
		["MEMBER", "ADMIN", false],
		["MEMBER", "OWNER", false],
		// No membership fails closed against every minimum
		[undefined, "MEMBER", false],
		[undefined, "ADMIN", false],
		[null, "OWNER", false],
	];

	it.each(cases)("role %s with minRole %s → %s", (role, minRole, expected) => {
		expect(hasMinimumWorkspaceRole(role, minRole)).toBe(expected);
	});

	it("fails closed on a role the client does not know", () => {
		// The server can ship a new role before the client does. Ranking it as unknown-therefore-
		// denied is what makes that deploy order safe; throwing or admitting would not be.
		expect(hasMinimumWorkspaceRole("SUPER_OWNER" as WorkspaceRole, "MEMBER")).toBe(false);
	});
});
