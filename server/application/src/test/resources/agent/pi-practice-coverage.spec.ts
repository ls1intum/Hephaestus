import { expect, test } from "bun:test";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { PracticeCoverageLedger } from "../../../main/resources/agent/pi-practice-coverage";
import { mapConcurrent } from "../../../main/resources/agent/pi-review-tree";

test("a watchdog abort leaves a complete atomic coverage snapshot", async () => {
	const directory = mkdtempSync(join(tmpdir(), "pi-practice-coverage-"));
	try {
		const eligible = ["a", "b", "c", "d"];
		const abort = new AbortController();
		const path = join(directory, "practice-coverage.json");
		const ledger = new PracticeCoverageLedger(path, eligible);

		expect(JSON.parse(readFileSync(path, "utf8"))).toEqual({
			eligible: 4,
			evaluated: 0,
			outcomes: eligible.map((practiceSlug) => ({ practiceSlug, outcome: "NOT_REACHED" })),
		});

		await mapConcurrent(
			eligible,
			1,
			(slug, index) => {
				ledger.markEvaluated([slug]);
				if (index === 1) abort.abort();
			},
			abort.signal,
		);

		expect(JSON.parse(readFileSync(path, "utf8"))).toEqual({
			eligible: 4,
			evaluated: 2,
			outcomes: [
				{ practiceSlug: "a", outcome: "EVALUATED" },
				{ practiceSlug: "b", outcome: "EVALUATED" },
				{ practiceSlug: "c", outcome: "NOT_REACHED" },
				{ practiceSlug: "d", outcome: "NOT_REACHED" },
			],
		});
	} finally {
		rmSync(directory, { recursive: true, force: true });
	}
});

test("the ledger rejects outcomes outside its eligible practice set", () => {
	const directory = mkdtempSync(join(tmpdir(), "pi-practice-coverage-"));
	try {
		const ledger = new PracticeCoverageLedger(join(directory, "practice-coverage.json"), [
			"eligible",
		]);
		expect(() => ledger.markEvaluated(["eligible", "unknown"])).toThrow(
			"evaluated practice is not eligible: unknown",
		);
		expect(
			JSON.parse(readFileSync(join(directory, "practice-coverage.json"), "utf8")),
		).toMatchObject({
			evaluated: 0,
		});
	} finally {
		rmSync(directory, { recursive: true, force: true });
	}
});
