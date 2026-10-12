import { describe, expect, it } from "vitest";
import { revisionLabel } from "./review-format";

describe("revisionLabel", () => {
	it("drops the scheme marker of a stored revision", () => {
		expect(revisionLabel("sha~e4aa1bda7dbc9")).toBe("e4aa1bda7dbc9");
		expect(revisionLabel("dig~0123abcd")).toBe("0123abcd");
	});

	it("leaves a plain commit hash alone", () => {
		expect(revisionLabel("e4aa1bda7dbc9")).toBe("e4aa1bda7dbc9");
	});
});
