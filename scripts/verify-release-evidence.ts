import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { isDeepStrictEqual } from "node:util";

import { validateReleaseSbom } from "./check-release-sbom.ts";
import { evaluate } from "./check-release-vulnerabilities.ts";
import {
	releaseCertificateIdentity,
	releaseIdentityFor,
	releaseOwner,
	releaseRepository,
} from "./lib/release-identities.ts";
import { isRelease } from "./release-image-lock.ts";

/** One platform of one image, as the manifest records it. `generate-release-evidence.ts` emits
 * these and this file is the only thing that decides whether they are acceptable. */
export type Subject = {
	digest: string;
	image: string;
	indexDigest: string;
	platform: "linux/amd64" | "linux/arm64";
	provenance: "first-party" | "upstream";
	repository: string;
};

type Manifest = { schemaVersion: 1; subjects: Subject[] };
type JsonObject = Record<string, unknown>;
type ExpectedImage = {
	indexDigest?: string;
	provenance: Subject["provenance"];
	repository: string;
};
type VerificationMode = "verify" | "verify-signatures" | "write-validation";

const digestPattern = /^sha256:[a-f0-9]{64}$/;
const imagePattern = /^[a-z0-9-]+$/;

function record(value: unknown): value is JsonObject {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readJson(path: string): unknown {
	return JSON.parse(readFileSync(path, "utf8")) as unknown;
}

function serialized(value: unknown): string {
	return `${JSON.stringify(value, null, 2)}\n`;
}

function persistOrVerify(path: string, value: unknown, writeValidation: boolean): void {
	if (writeValidation) {
		writeFileSync(path, serialized(value));
		return;
	}
	if (readFileSync(path, "utf8") !== serialized(value))
		throw new Error(`${path} changed when re-derived`);
}

export function validateLicenseReport(value: unknown, reference: string): void {
	if (!record(value) || value.ArtifactName !== reference || !Array.isArray(value.Results))
		throw new Error(`license report is not bound to ${reference}`);
}

export function indexContainsSubject(value: unknown, subject: Subject): boolean {
	if (!record(value) || !Array.isArray(value.manifests)) throw new Error("OCI index is malformed");
	const architecture = subject.platform.slice("linux/".length);
	return value.manifests.some((entry) => {
		if (!record(entry) || !record(entry.platform)) return false;
		return (
			entry.digest === subject.digest &&
			entry.platform.os === "linux" &&
			entry.platform.architecture === architecture
		);
	});
}

export function attestationContainsSbom(value: unknown, sbom: unknown): boolean {
	if (!Array.isArray(value)) throw new Error("Cosign attestation result is malformed");
	return value.some((entry) => {
		if (!record(entry) || typeof entry.payload !== "string") return false;
		try {
			const envelope: unknown = JSON.parse(Buffer.from(entry.payload, "base64").toString("utf8"));
			return record(envelope) && isDeepStrictEqual(envelope.predicate, sbom);
		} catch {
			return false;
		}
	});
}

export function validateManifest(
	value: unknown,
	inventoryValue: unknown,
	firstPartyNamespace: string,
): Manifest {
	if (!record(value) || value.schemaVersion !== 1 || !Array.isArray(value.subjects))
		throw new Error("release manifest must use schema version 1 and contain subjects");
	if (
		!record(inventoryValue) ||
		inventoryValue.schemaVersion !== 1 ||
		!Array.isArray(inventoryValue.images) ||
		!Array.isArray(inventoryValue.upstream)
	)
		throw new Error("release image inventory is malformed");
	if (
		!inventoryValue.images.every((image) => typeof image === "string" && imagePattern.test(image))
	)
		throw new Error("release image inventory contains an invalid image");
	const images = inventoryValue.images.filter(
		(image): image is string => typeof image === "string",
	);
	if (new Set(images).size !== images.length) throw new Error("duplicate release image");
	const expectedImages = new Map<string, ExpectedImage>();
	for (const image of images)
		expectedImages.set(image, {
			provenance: "first-party",
			repository: `${firstPartyNamespace}/${image}`,
		});
	for (const item of inventoryValue.upstream) {
		if (
			!record(item) ||
			typeof item.name !== "string" ||
			!imagePattern.test(item.name) ||
			typeof item.repository !== "string" ||
			!item.repository ||
			typeof item.digest !== "string" ||
			!digestPattern.test(item.digest)
		)
			throw new Error("release image inventory contains an invalid upstream image");
		if (expectedImages.has(item.name)) throw new Error(`duplicate release image: ${item.name}`);
		expectedImages.set(item.name, {
			indexDigest: item.digest,
			provenance: "upstream",
			repository: item.repository,
		});
	}
	const subjects: Subject[] = value.subjects.map((item, index) => {
		if (
			!record(item) ||
			typeof item.image !== "string" ||
			!imagePattern.test(item.image) ||
			(item.platform !== "linux/amd64" && item.platform !== "linux/arm64") ||
			typeof item.digest !== "string" ||
			!digestPattern.test(item.digest) ||
			typeof item.indexDigest !== "string" ||
			!digestPattern.test(item.indexDigest) ||
			typeof item.repository !== "string" ||
			(item.provenance !== "first-party" && item.provenance !== "upstream")
		)
			throw new Error(`release manifest subject ${index} is malformed`);
		const expected = expectedImages.get(item.image);
		if (
			!expected ||
			expected.repository !== item.repository ||
			expected.provenance !== item.provenance ||
			(expected.indexDigest && expected.indexDigest !== item.indexDigest)
		)
			throw new Error(`release manifest subject ${item.image} does not match its inventory`);
		return {
			digest: item.digest,
			image: item.image,
			indexDigest: item.indexDigest,
			platform: item.platform,
			provenance: item.provenance,
			repository: item.repository,
		};
	});
	if (
		new Set(subjects.map(({ image, platform }) => `${image}\0${platform}`)).size !== subjects.length
	)
		throw new Error("release manifest contains duplicate image platforms");
	if (new Set(subjects.map(({ image }) => image)).size !== expectedImages.size)
		throw new Error("release manifest does not match the image inventory");
	for (const image of expectedImages.keys()) {
		const imageSubjects = subjects.filter((subject) => subject.image === image);
		const platforms = imageSubjects.map(({ platform }) => platform);
		if (!isDeepStrictEqual(platforms, ["linux/amd64", "linux/arm64"]))
			throw new Error(`${image} must have exactly amd64 and arm64 evidence in canonical order`);
		if (new Set(imageSubjects.map(({ indexDigest }) => indexDigest)).size !== 1)
			throw new Error(`${image} subjects must share one index digest`);
	}
	return { schemaVersion: 1, subjects };
}

function command(commandName: string, args: string[], capture = false): string {
	const result = spawnSync(commandName, args, {
		encoding: "utf8",
		stdio: capture ? "pipe" : "inherit",
	});
	if (result.error) throw result.error;
	if (result.status !== 0) {
		const detail = capture && result.stderr.trim() ? `: ${result.stderr.trim()}` : "";
		throw new Error(`${commandName} failed with exit code ${result.status ?? "unknown"}${detail}`);
	}
	return result.stdout;
}

export function verifyReleaseEvidence(
	directory: string,
	mode: VerificationMode = "verify",
): Manifest {
	const manifestValue = readJson(join(directory, "manifest.json"));
	if (
		!record(manifestValue) ||
		typeof manifestValue.release !== "string" ||
		!isRelease(manifestValue.release)
	)
		throw new Error("release manifest must name the release it evidences");
	// The evidence may belong to a release published under a pre-transfer namespace and
	// signed by the pre-transfer repository, both of which it keeps forever (issue
	// #1599) — resolve namespace *and* signer per version, never from the run context.
	const release = manifestValue.release;
	const manifest = validateManifest(
		manifestValue,
		readJson(join(directory, "release-images.json")),
		releaseIdentityFor(release).namespace,
	);
	const policy = readJson(join(directory, "vulnerability-policy.json"));
	for (const subject of manifest.subjects) {
		const suffix = subject.platform.replace("/", "-");
		const prefix = join(directory, `${subject.image}-${suffix}`);
		const sbom = validateReleaseSbom(
			readJson(`${prefix}.syft.json`),
			readJson(`${prefix}.spdx.json`),
			readJson(`${prefix}.cdx.json`),
			subject,
		);
		persistOrVerify(`${prefix}.sbom-validation.json`, sbom, mode === "write-validation");
		const reference = `${subject.repository}@${subject.digest}`;
		validateLicenseReport(readJson(`${prefix}.license.json`), reference);
		const result = evaluate(subject.image, readJson(`${prefix}.trivy.json`), policy, new Date(), {
			digest: subject.digest,
			platform: subject.platform,
			reference,
		});
		const policyResult = {
			image: subject.image,
			platform: subject.platform,
			digest: subject.digest,
			status: result.errors.length === 0 && result.rejected.length === 0 ? "pass" : "fail",
			...result,
		};
		persistOrVerify(`${prefix}.policy.json`, policyResult, mode === "write-validation");
		if (result.errors.length > 0 || result.rejected.length > 0)
			throw new Error(`${subject.image} does not satisfy vulnerability policy`);
		const index = readJsonFromCommand("docker", [
			"buildx",
			"imagetools",
			"inspect",
			`${subject.repository}@${subject.indexDigest}`,
			"--raw",
		]);
		if (!indexContainsSubject(index, subject))
			throw new Error(`${subject.image} index does not contain ${subject.platform} digest`);
		if (mode === "verify-signatures" && subject.provenance === "first-party") {
			const attestations = readJsonFromCommand("cosign", [
				"verify-attestation",
				"--type",
				"spdxjson",
				"--certificate-identity",
				releaseCertificateIdentity(release, process.env),
				"--certificate-oidc-issuer",
				"https://token.actions.githubusercontent.com",
				reference,
			]);
			if (!attestationContainsSbom(attestations, readJson(`${prefix}.spdx.json`)))
				throw new Error(`${subject.image} SBOM attestation does not match its durable evidence`);
		}
	}
	if (mode === "verify-signatures") verifyIndexSignatures(manifest, release);
	return manifest;
}

function readJsonFromCommand(name: string, args: string[]): unknown {
	return JSON.parse(command(name, args, true)) as unknown;
}

function verifyIndexSignatures(manifest: Manifest, release: string): void {
	// The image indexes are signed by reusable-docker-build.yml in the repository that
	// built them, which for a pre-transfer release is the pre-transfer slug (#1599).
	const repository = releaseRepository(release, process.env);
	const owner = releaseOwner(release, process.env);
	const indexes = new Map(
		manifest.subjects
			.filter(({ provenance }) => provenance === "first-party")
			.map(({ repository: imageRepository, indexDigest }) => [
				`${imageRepository}@${indexDigest}`,
				true,
			]),
	);
	for (const reference of indexes.keys()) {
		command(
			"cosign",
			[
				"verify",
				reference,
				"--certificate-identity",
				releaseCertificateIdentity(release, process.env, undefined, "reusable-docker-build.yml"),
				"--certificate-oidc-issuer",
				"https://token.actions.githubusercontent.com",
				"--certificate-github-workflow-repository",
				repository,
			],
			true,
		);
		command(
			"gh",
			[
				"attestation",
				"verify",
				`oci://${reference}`,
				"--owner",
				owner,
				"--signer-workflow",
				`${repository}/.github/workflows/reusable-docker-build.yml`,
			],
			true,
		);
	}
}

if (import.meta.main) {
	const [directory, option] = process.argv.slice(2);
	if (!directory)
		throw new Error(
			"usage: verify-release-evidence <evidence-directory> [--verify-signatures|--write-validation]",
		);
	let mode: VerificationMode;
	switch (option) {
		case undefined:
			mode = "verify";
			break;
		case "--verify-signatures":
			mode = "verify-signatures";
			break;
		case "--write-validation":
			mode = "write-validation";
			break;
		default:
			throw new Error(
				"usage: verify-release-evidence <evidence-directory> [--verify-signatures|--write-validation]",
			);
	}
	verifyReleaseEvidence(directory, mode);
}
