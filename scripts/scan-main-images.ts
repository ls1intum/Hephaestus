/**
 * Rescans the images built from `main` against the release vulnerability policy.
 *
 * Container CVEs are time-dependent, not commit-dependent: the commit that was clean when it merged
 * becomes vulnerable days later with nothing in the repository having changed. The build-time gate in
 * `reusable-docker-build.yml` catches change-driven regressions and cannot catch this, so v0.75.0 was
 * blocked by findings whose first appearance was the release itself.
 *
 * This walks `security/release-images.json`, resolves each image's `:main` tag to its `linux/amd64`
 * digest, scans it, and hands the report to `check-release-vulnerabilities.ts` — the same evaluator and
 * the same `security/vulnerability-policy.json` the build gate and the release use. A second copy of
 * either is precisely the failure this whole effort removes, so the policy is evaluated by invoking
 * that script rather than by reimplementing it.
 *
 * A finding never fails this run. `report-vulnerability-drift.ts` reads the `.policy.json` files
 * written here and routes them to a tracking issue; only an infrastructure failure — a missing tag, an
 * unreachable registry, a Trivy crash — is worth a red status on a schedule nobody triggered.
 */
import { existsSync } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

import { asArray, asRecord, asString, isRecord, readJsonFile } from "./lib/json.ts";
import { output, run, succeeds } from "./lib/process.ts";

/** The one platform scanned. Alpine and Debian ship the same package versions across
 * architectures, and the release still scans both, where the evidence bundle must be complete. */
export const PLATFORM = "linux/amd64";

const DIGEST = /^sha256:[a-f0-9]{64}$/;

export interface Subject {
	/** Short image name, as `security/release-images.json` and the policy exceptions spell it. */
	readonly image: string;
	readonly reference: string;
	readonly repository: string;
}

export interface ScanOutcome {
	readonly image: string;
	/** `false` when the policy evaluator rejected something; never throws the run. */
	readonly passed: boolean;
}

/**
 * The first-party images to scan, in inventory order.
 *
 * `reusable-docker-build.yml` tags every push with `github.ref_name`, so `<registry>/<owner>/<image>:main`
 * already names main's HEAD build and nothing new has to be published for this to have a subject.
 */
export function planSubjects(inventory: unknown, registry: string, tag: string): Subject[] {
	const images = asArray(asRecord(inventory, "release image inventory").images, "images");
	if (images.length === 0) throw new Error("release image inventory lists no images");
	return images.map((value, index) => {
		const image = asString(value, `images[${index}]`);
		if (!/^[a-z0-9-]+$/.test(image)) throw new Error(`malformed release image name: ${image}`);
		const repository = `${registry}/${image}`;
		return { image, reference: `${repository}:${tag}`, repository };
	});
}

/**
 * The `linux/amd64` digest inside `docker buildx imagetools inspect --raw` output, or `undefined`
 * when the document is a single manifest rather than an index and the caller must ask for its digest
 * directly. Multi-architecture builds also push an attestation manifest, whose platform is
 * `unknown/unknown`, so the architecture has to be matched rather than the position assumed.
 */
export function selectPlatformDigest(raw: unknown, platform: string): string | undefined {
	const document = asRecord(raw, "imagetools manifest");
	if (!Array.isArray(document.manifests)) return undefined;
	const [os, architecture] = platform.split("/");
	for (const entry of document.manifests) {
		if (!isRecord(entry) || !isRecord(entry.platform)) continue;
		if (entry.platform.os !== os || entry.platform.architecture !== architecture) continue;
		if (typeof entry.digest === "string") return entry.digest;
	}
	return undefined;
}

async function resolveDigest(subject: Subject, platform: string): Promise<string> {
	const raw: unknown = JSON.parse(
		await output("docker", ["buildx", "imagetools", "inspect", subject.reference, "--raw"]),
	);
	const digest =
		selectPlatformDigest(raw, platform) ??
		(
			await output("docker", [
				"buildx",
				"imagetools",
				"inspect",
				"--format",
				"{{.Manifest.Digest}}",
				subject.reference,
			])
		).trim();
	if (!DIGEST.test(digest))
		throw new Error(`no ${platform} digest for ${subject.reference} (got: ${digest || "<empty>"})`);
	return digest;
}

/** `webapp-linux-amd64`, the stem both report files and the uploaded artifact are named by. */
export function reportStem(image: string, platform: string): string {
	return `${image}-${platform.replaceAll("/", "-")}`;
}

async function scan(subject: Subject, directory: string, platform: string): Promise<ScanOutcome> {
	const digest = await resolveDigest(subject, platform);
	const stem = reportStem(subject.image, platform);
	const report = path.join(directory, `${stem}.json`);
	await run("trivy", [
		"image",
		"--skip-db-update",
		"--scanners",
		"vuln",
		"--format",
		"json",
		"--output",
		report,
		`${subject.repository}@${digest}`,
	]);
	const result = path.join(directory, `${stem}.policy.json`);
	const evaluator = [
		path.join(import.meta.dirname, "check-release-vulnerabilities.ts"),
		subject.image,
		platform,
		digest,
		subject.repository,
		report,
		"security/vulnerability-policy.json",
		result,
	];
	// Silenced, not ignored: the evaluator writes `::error::` per rejected finding, and a green job
	// carrying error annotations is how a team learns to read past them. The result file it wrote
	// names every finding, and report-vulnerability-drift.ts routes them to a human from there.
	if (await succeeds("node", evaluator)) return { image: subject.image, passed: true };
	if (!existsSync(result)) {
		// It threw before writing anything — a malformed report or policy, not a finding. Re-run so
		// the reason reaches the log, then fail: this is an infrastructure failure, and unlike a CVE
		// it is fixed by a commit.
		await run("node", evaluator);
		throw new Error(`vulnerability policy evaluation produced no result for ${subject.image}`);
	}
	return { image: subject.image, passed: false };
}

export async function scanAll(
	subjects: readonly Subject[],
	directory: string,
	platform = PLATFORM,
): Promise<ScanOutcome[]> {
	await mkdir(directory, { recursive: true });
	const outcomes: ScanOutcome[] = [];
	for (const subject of subjects) outcomes.push(await scan(subject, directory, platform));
	return outcomes;
}

if (import.meta.main) {
	const [directory = "reports"] = process.argv.slice(2);
	const registry = process.env.IMAGE_REGISTRY ?? "ghcr.io/hephaestus-build";
	const tag = process.env.IMAGE_TAG ?? "main";
	const subjects = planSubjects(await readJsonFile("security/release-images.json"), registry, tag);
	const outcomes = await scanAll(subjects, directory);
	await writeFile(
		path.join(directory, "scan.json"),
		`${JSON.stringify({ platform: PLATFORM, registry, scannedAt: new Date().toISOString(), subjects, tag }, null, 2)}\n`,
	);
	for (const outcome of outcomes)
		process.stdout.write(`${outcome.image}: ${outcome.passed ? "pass" : "fail"}\n`);
}
