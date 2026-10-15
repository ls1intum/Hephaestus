import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";
import { deflateSync } from "node:zlib";

import { isJsonObject } from "../../../../../../docker/agents/practice-scripts/lib/core/json.ts";
import { scan } from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";
import { PRACTICES_DIR } from "./golden-review-workspace.ts";
import { capturedLines, field, runRunner, stagePractices } from "./program-host-runner.ts";

const FILE = "Auth.ts";
const TEXT = "insecure();\n";
const SOURCE_PATH = "repos/primary/.git/HEAD";
const CAPTURED_REFS = "repos/primary/.git/hephaestus-captured-refs";

function stageScan(cwd: string): string {
	stagePractices(cwd, [{ slug: "auth-review" }]);
	rmSync(path.join(cwd, "evidence/change.json"));
	const git = path.join(cwd, "repos/primary/.git");
	const object = (kind: string, bytes: Buffer) => {
		const content = Buffer.concat([Buffer.from(`${kind} ${bytes.length}\0`), bytes]);
		const id = createHash("sha1").update(content).digest("hex");
		const folder = path.join(git, "objects", id.slice(0, 2));
		mkdirSync(folder, { recursive: true });
		writeFileSync(path.join(folder, id.slice(2)), deflateSync(content));
		return id;
	};
	const blob = object("blob", Buffer.from(TEXT));
	const tree = object(
		"tree",
		Buffer.concat([Buffer.from(`100644 ${FILE}\0`), Buffer.from(blob, "hex")]),
	);
	const commit = object(
		"commit",
		Buffer.from(
			`tree ${tree}\nauthor Test <test@example.org> 0 +0000\ncommitter Test <test@example.org> 0 +0000\n\nScan fixture\n`,
		),
	);
	mkdirSync(path.join(git, "refs"), { recursive: true });
	writeFileSync(path.join(git, "HEAD"), `${commit}\n`);
	writeFileSync(path.join(cwd, CAPTURED_REFS), `${commit} HEAD\n`);
	writeFileSync(path.join(cwd, "repos/primary", FILE), TEXT);
	const inputs = scan(FILE);
	writeFileSync(
		path.join(cwd, PRACTICES_DIR, "auth-review.inputs.json"),
		JSON.stringify({
			...inputs,
			item: { ...inputs.item, commitId: commit, blobId: blob },
			practice: { ...inputs.practice, slug: "auth-review" },
			scope: { ...inputs.scope, version: `scm.file:v1:${commit}:${"b".repeat(64)}` },
		}),
	);
	writeFileSync(
		path.join(cwd, "catalog/practices/index.json"),
		JSON.stringify([
			{
				slug: "auth-review",
				name: "Authentication review",
				group: "security",
				versionId: "1",
				readsSources: ["scm.repository.tree"],
				exhaustiveSources: ["scm.repository.tree"],
				contextPaths: [],
			},
		]),
	);
	writeFileSync(
		path.join(cwd, "evidence/manifest.json"),
		JSON.stringify({
			itemKind: "scm.file",
			files: [SOURCE_PATH, CAPTURED_REFS].map((witness) => ({
				kind: "scm.repository.tree",
				file: { path: witness },
			})),
			sources: [
				{
					kind: "scm.repository.tree",
					citation: { side: "FORBIDDEN", commit: "OPTIONAL" },
					state: { availability: "AVAILABLE" },
					files: [{ path: SOURCE_PATH }],
				},
			],
		}),
	);
	return commit;
}

for (const basis of ["state", "introduced"] as const) {
	void test(`SCAN ${basis === "state" ? "admits state with complete pinned-file receipts" : "rejects introduced basis"} through tool validation`, async () => {
		const cwd = mkdtempSync(path.join(tmpdir(), "pi-runner-scan-"));
		try {
			const commit = stageScan(cwd);
			const candidate = {
				outcome: "NOT_MET",
				summary: "Authentication uses an insecure call",
				explanation: "The pinned source calls insecure().",
				citations: [],
				evidence: {},
				problems: [
					{
						severity: "MAJOR",
						summary: "Authentication uses an insecure call",
						explanation: "The source calls insecure() without checked().",
						citations: [
							{
								sourceKind: "scm.repository.tree",
								sourcePath: SOURCE_PATH,
								path: FILE,
								commit,
								startLine: 1,
								endLine: 1,
								quote: "insecure();",
							},
						],
						basis: {
							type: basis,
							...(basis === "introduced" ? { head: 0 } : {}),
							explanation: "The pinned file contains the insecure call.",
						},
					},
				],
			};
			const exit = await runRunner(
				cwd,
				{},
				{
					PI_HOST_SCAN_PATH: FILE,
					PI_HOST_SESSION_CANDIDATE: JSON.stringify(candidate),
					PI_PRACTICE_CONCURRENCY: "3",
				},
			);
			assert.deepEqual(capturedLines(cwd, "scan-read")[0], {
				content: [{ type: "text", text: TEXT }],
				details: { status: "FOUND", truncated: false },
			});
			if (basis === "introduced") {
				assert.equal(exit.status, 1, exit.stderr);
				assert.match(
					exit.stderr,
					/basis_not_allowed: problems\.0\.basis: a scan needs state basis/u,
				);
				assert.deepEqual(capturedLines(cwd, "admission"), []);
				return;
			}
			assert.equal(exit.status, 0, exit.stderr);
			assert.match(exit.stderr, /Practice concurrency: configured=3, effective=1/u);
			const [request] = capturedLines(cwd, "admission");
			const body = field(request, "body");
			const results = field(body, "results");
			assert.ok(Array.isArray(results));
			assert.equal(results.length, 1);
			const submitted = field(results[0], "candidate");
			assert.ok(isJsonObject(submitted));
			const { coverage, ...measured } = submitted;
			assert.deepEqual(measured, candidate);
			assert.partialDeepStrictEqual(coverage, {
				source: "host",
				reads: 2,
				complete: true,
				failedReads: 0,
				truncatedReads: 0,
			});
			const receipts = field(coverage, "receipts");
			assert.ok(Array.isArray(receipts));
			assert.equal(receipts.length, 1);
			assert.deepEqual(field(results[0], "readReceipts"), receipts);
			for (const receipt of receipts) {
				assert.partialDeepStrictEqual(receipt, {
					sourceKind: "scm.repository.tree",
					sourcePath: SOURCE_PATH,
					path: FILE,
					version: commit,
					status: "COMPLETE",
					startLine: 1,
					endLine: 2,
					contentDigest: createHash("sha256").update(TEXT).digest("hex"),
				});
			}
		} finally {
			rmSync(cwd, { recursive: true, force: true });
		}
	});
}
