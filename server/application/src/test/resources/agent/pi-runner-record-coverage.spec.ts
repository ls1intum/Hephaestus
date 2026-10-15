import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { z } from "../../../../../../docker/agents/practice-scripts/lib/core/practice-script.ts";
import {
	capturedLines,
	runRunner,
	SESSION_CANDIDATE,
	stagePractices,
} from "./program-host-runner.ts";

const fixture = z
	.object({
		contents: z.record(z.string(), z.string()),
		manifest: z.object({
			files: z.array(z.unknown()),
			sources: z.array(z.unknown()),
			citableTexts: z.array(z.unknown()),
		}),
	})
	.parse(
		JSON.parse(readFileSync(new URL("record-coverage-capture.json", import.meta.url), "utf8")),
	);
const manifestSchema = z.looseObject({
	files: z.array(z.looseObject({ kind: z.string(), file: z.object({ path: z.string() }) })),
	sources: z.array(z.looseObject({ kind: z.string() })),
});

for (const gap of [false, true]) {
	void test(`a Java capture manifest with extracted record copies and aliases ${gap ? "retains a real unread gap" : "permits MET from the complete parent record"}`, async (t) => {
		const cwd = mkdtempSync(path.join(tmpdir(), "pi-record-coverage-"));
		t.after(() => rmSync(cwd, { recursive: true, force: true }));
		stagePractices(cwd, [{ slug: "test-practice" }]);
		const manifestPath = path.join(cwd, "evidence/manifest.json");
		const manifest = manifestSchema.parse(JSON.parse(readFileSync(manifestPath, "utf8")));
		for (const [file, text] of Object.entries(fixture.contents)) {
			mkdirSync(path.dirname(path.join(cwd, file)), { recursive: true });
			writeFileSync(path.join(cwd, file), text);
		}
		const files = [
			...manifest.files.filter((file) => file.kind !== "scm.pull-request.core"),
			...fixture.manifest.files,
		];
		if (gap) {
			writeFileSync(path.join(cwd, "evidence/unseen.json"), '{"body":"Independent evidence"}');
			files.push({ kind: "scm.pull-request.core", file: { path: "evidence/unseen.json" } });
		}
		writeFileSync(
			manifestPath,
			JSON.stringify({
				...manifest,
				files,
				sources: [
					...manifest.sources.filter((source) => source.kind !== "scm.pull-request.core"),
					...fixture.manifest.sources,
				],
				citableTexts: fixture.manifest.citableTexts,
			}),
		);
		const candidate = {
			...SESSION_CANDIDATE,
			problems: undefined,
			outcome: "MET",
			citations: [
				{
					sourceKind: "scm.pull-request.core",
					sourcePath: "evidence/metadata.json",
					path: "evidence/metadata.json",
					startLine: 1,
					endLine: 1,
					quote: fixture.contents["evidence/metadata.json"],
				},
			],
		};
		const exit = await runRunner(cwd, {}, { PI_HOST_SESSION_CANDIDATE: JSON.stringify(candidate) });
		assert.equal(exit.status, gap ? 1 : 0, exit.stderr);
		if (gap) {
			assert.match(exit.stderr, /unseen\.json/u);
		} else {
			assert.equal(capturedLines(cwd, "submitted").length, 1);
		}
	});
}
