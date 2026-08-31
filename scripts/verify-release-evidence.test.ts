import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
	attestationContainsSbom,
	indexContainsSubject,
	validateLicenseReport,
	validateManifest,
} from "./verify-release-evidence.ts";

const digest = `sha256:${"a".repeat(64)}`;
const inventory = { schemaVersion: 1, images: ["server"], upstream: [] };
const subject = (platform: "linux/amd64" | "linux/arm64") => ({
	digest,
	image: "server",
	indexDigest: digest,
	platform,
	provenance: "first-party" as const,
	repository: "ghcr.io/ls1intum/hephaestus/server",
});

void describe("release evidence manifest", () => {
	void it("accepts exactly one canonical subject for each supported platform", () => {
		const result = validateManifest(
			{ schemaVersion: 1, subjects: [subject("linux/amd64"), subject("linux/arm64")] },
			inventory,
		);
		assert.equal(result.subjects.length, 2);
	});

	void it("fails closed on missing, reordered, and duplicate platforms", () => {
		assert.throws(
			() => validateManifest({ schemaVersion: 1, subjects: [subject("linux/amd64")] }, inventory),
			/canonical order/,
		);
		assert.throws(
			() =>
				validateManifest(
					{ schemaVersion: 1, subjects: [subject("linux/arm64"), subject("linux/amd64")] },
					inventory,
				),
			/canonical order/,
		);
		assert.throws(
			() =>
				validateManifest(
					{ schemaVersion: 1, subjects: [subject("linux/amd64"), subject("linux/amd64")] },
					inventory,
				),
			/duplicate image platforms/,
		);
	});

	void it("rejects path traversal, mutable identities, wrong repositories, and invalid provenance", () => {
		const valid = subject("linux/amd64");
		for (const invalid of [
			{ ...valid, image: "../manifest" },
			{ ...valid, digest: "latest" },
			{ ...valid, repository: "ghcr.io/attacker/server" },
			{ ...valid, provenance: "upstream" },
			{ ...valid, provenance: "unknown" },
		]) {
			assert.throws(
				() =>
					validateManifest(
						{ schemaVersion: 1, subjects: [invalid, subject("linux/arm64")] },
						inventory,
					),
				/malformed|does not match/,
			);
		}
	});

	void it("rejects duplicate or malformed inventory entries", () => {
		const subjects = [subject("linux/amd64"), subject("linux/arm64")];
		assert.throws(
			() =>
				validateManifest(
					{ schemaVersion: 1, subjects },
					{ schemaVersion: 1, images: ["server", "server"], upstream: [] },
				),
			/duplicate release image/,
		);
		assert.throws(
			() =>
				validateManifest(
					{ schemaVersion: 1, subjects },
					{ schemaVersion: 1, images: ["../server"], upstream: [] },
				),
			/invalid image/,
		);
	});

	void it("derives provenance and index identity from the inventory", () => {
		const upstreamDigest = `sha256:${"b".repeat(64)}`;
		const upstreamInventory = {
			schemaVersion: 1,
			images: [],
			upstream: [
				{
					digest: upstreamDigest,
					name: "postgres",
					repository: "docker.io/library/postgres",
				},
			],
		};
		const upstreamSubject = (platform: "linux/amd64" | "linux/arm64") => ({
			digest,
			image: "postgres",
			indexDigest: upstreamDigest,
			platform,
			provenance: "upstream" as const,
			repository: "docker.io/library/postgres",
		});
		const valid = [upstreamSubject("linux/amd64"), upstreamSubject("linux/arm64")];
		assert.doesNotThrow(() =>
			validateManifest({ schemaVersion: 1, subjects: valid }, upstreamInventory),
		);
		assert.throws(
			() =>
				validateManifest(
					{ schemaVersion: 1, subjects: [{ ...valid[0], provenance: "first-party" }, valid[1]] },
					upstreamInventory,
				),
			/does not match its inventory/,
		);
		assert.throws(
			() =>
				validateManifest(
					{ schemaVersion: 1, subjects: [{ ...valid[0], indexDigest: digest }, valid[1]] },
					upstreamInventory,
				),
			/does not match its inventory/,
		);
		assert.throws(
			() =>
				validateManifest(
					{
						schemaVersion: 1,
						subjects: [
							subject("linux/amd64"),
							{ ...subject("linux/arm64"), indexDigest: upstreamDigest },
						],
					},
					inventory,
				),
			/one index digest/,
		);
	});
});

void describe("release evidence bindings", () => {
	void it("binds license and OCI index evidence to the immutable subject", () => {
		const amd64 = subject("linux/amd64");
		const reference = `${amd64.repository}@${amd64.digest}`;
		assert.doesNotThrow(() =>
			validateLicenseReport({ ArtifactName: reference, Results: [] }, reference),
		);
		assert.throws(() => validateLicenseReport({ ArtifactName: "wrong", Results: [] }, reference));
		assert.equal(
			indexContainsSubject(
				{ manifests: [{ digest, platform: { os: "linux", architecture: "amd64" } }] },
				amd64,
			),
			true,
		);
		assert.equal(
			indexContainsSubject(
				{ manifests: [{ digest, platform: { os: "linux", architecture: "arm64" } }] },
				amd64,
			),
			false,
		);
	});

	void it("requires a Cosign payload whose predicate exactly matches the durable SBOM", () => {
		const sbom = { packages: [{ name: "example", version: "1" }] };
		const payload = (predicate: unknown): string =>
			Buffer.from(JSON.stringify({ predicate })).toString("base64");
		assert.equal(attestationContainsSbom([{ payload: payload(sbom) }], sbom), true);
		assert.equal(attestationContainsSbom([{ payload: payload({ packages: [] }) }], sbom), false);
		assert.equal(attestationContainsSbom([{ payload: "not-base64-json" }], sbom), false);
	});
});
