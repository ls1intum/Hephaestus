import { describe, expect, it } from "vitest";
import type { EvidenceCitation } from "@/api/types.gen";
import { evidenceLineRangeLabel, splitPath, toEvidenceLocations } from "./evidence";

function citation(overrides: Partial<EvidenceCitation> = {}): EvidenceCitation {
	return {
		sourceKind: "scm.pull-request.diff",
		artifactPath: "owner/repo#1",
		path: "src/Main.java",
		side: "NEW",
		startLine: 42,
		endLine: 44,
		quote: "a();\nb();",
		quoteRedacted: false,
		...overrides,
	};
}

describe("toEvidenceLocations", () => {
	it("carries a quote with the lines it came from", () => {
		expect(toEvidenceLocations({ citations: [citation()] })).toStrictEqual([
			{
				path: "src/Main.java",
				startLine: 42,
				endLine: 44,
				snippet: "a();\nb();",
				redacted: false,
			},
		]);
	});

	it("keeps a redacted citation, so a withheld quote stays distinguishable from an unquoted one", () => {
		const [location] = toEvidenceLocations({
			citations: [citation({ quote: undefined, quoteRedacted: true })],
		});

		expect(location?.snippet).toBeUndefined();
		expect(location?.redacted).toBe(true);
		// The place is still named: a reader learns WHERE the reviewer looked even when the quote is withheld.
		expect(location?.path).toBe("src/Main.java");
	});

	it("preserves the order the reviewer recorded", () => {
		const locations = toEvidenceLocations({
			citations: [citation({ path: "b.java" }), citation({ path: "a.java" })],
		});

		expect(locations.map((location) => location.path)).toStrictEqual(["b.java", "a.java"]);
	});

	it("treats absent evidence as no citations", () => {
		expect(toEvidenceLocations(undefined)).toStrictEqual([]);
		expect(toEvidenceLocations({ citations: [] })).toStrictEqual([]);
	});
});

describe("splitPath", () => {
	it("splits so the directory can absorb truncation", () => {
		expect(splitPath("src/main/java/Foo.java")).toStrictEqual({
			directory: "src/main/java/",
			fileName: "Foo.java",
		});
	});

	it("treats a bare file name as having no directory", () => {
		expect(splitPath("Foo.java")).toStrictEqual({ directory: "", fileName: "Foo.java" });
	});
});

describe("evidenceLineRangeLabel", () => {
	it("collapses a single-line range", () => {
		expect(evidenceLineRangeLabel({ path: "a", startLine: 62, endLine: 62, redacted: false })).toBe(
			"62",
		);
	});

	it("renders a real range", () => {
		expect(evidenceLineRangeLabel({ path: "a", startLine: 62, endLine: 70, redacted: false })).toBe(
			"62–70",
		);
	});
});
