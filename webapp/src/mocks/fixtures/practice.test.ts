import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";
import { z } from "zod";

import { mockPracticeDefinitionOptions } from "./practice";

const citationRequirement = z.enum(["REQUIRED", "OPTIONAL", "FORBIDDEN"]);

const catalog = z
	.object({
		sources: z.array(
			z.object({
				kind: z.string(),
				displayName: z.string(),
				itemKinds: z.array(z.string()),
				completeness: z.object({ supportsComplete: z.boolean() }),
				citation: z.object({ side: citationRequirement, revision: citationRequirement }),
			}),
		),
	})
	.parse(
		JSON.parse(
			readFileSync(
				`../server/application/src/main/resources/contracts/source-use/${mockPracticeDefinitionOptions.sourceContract}/catalog.json`,
				"utf8",
			),
		),
	);

/** The needs a source offers: Complete only where the source can be read whole. */
const needsOf = (supportsComplete: boolean | undefined) =>
	supportsComplete === true ? ["REQUIRED", "COMPLETE", "OPTIONAL"] : ["REQUIRED", "OPTIONAL"];

describe("practice definition options fixture", () => {
	it("matches its declared source contract for every offered read source", () => {
		for (const itemType of mockPracticeDefinitionOptions.itemTypes) {
			for (const option of itemType.readSources) {
				const source = catalog.sources.find((candidate) => candidate.kind === option.source);
				expect(source, option.source).toBeDefined();
				expect(source?.itemKinds, option.source).toContain(itemType.kind);
				expect(option, option.source).toStrictEqual({
					source: source?.kind,
					label: source?.displayName,
					needs: needsOf(source?.completeness.supportsComplete),
					citation: source?.citation,
				});
			}
		}
	});
});
