import assert from "node:assert/strict";
import { test } from "node:test";
import { validateReleaseSbom } from "./check-release-sbom.ts";

const digest = `sha256:${"a".repeat(64)}`;
const artifact = {
	name: "example",
	version: "1.2.3",
	type: "npm",
	purl: "pkg:npm/example@1.2.3",
	locations: [{ path: "/app/package.json" }],
	licenses: [{ value: "MIT" }],
};
const syft = {
	source: {
		type: "image",
		metadata: { manifestDigest: digest, os: "linux", architecture: "amd64" },
	},
	artifacts: [artifact],
};
const spdx = {
	spdxVersion: "SPDX-2.3",
	dataLicense: "CC0-1.0",
	documentNamespace: "https://example.com/sbom",
	packages: [
		{
			name: "example",
			versionInfo: "1.2.3",
			externalRefs: [{ referenceType: "purl", referenceLocator: artifact.purl }],
		},
	],
};
const cycloneDx = {
	bomFormat: "CycloneDX",
	specVersion: "1.6",
	components: [{ name: "example", version: "1.2.3", purl: artifact.purl }],
};

await test("proves subject binding and lossless package conversion", () => {
	assert.deepEqual(validateReleaseSbom(syft, spdx, cycloneDx, digest, "linux/amd64"), {
		schemaVersion: 1,
		digest,
		platform: "linux/amd64",
		packageCount: 1,
		packagesWithPurl: 1,
		packagesWithLicense: 1,
		packagesWithoutLicense: [],
	});
});

await test("rejects wrong subjects and packages lost during conversion", () => {
	assert.throws(
		() => validateReleaseSbom(syft, spdx, cycloneDx, `sha256:${"b".repeat(64)}`, "linux/amd64"),
		/wrong manifest/,
	);
	assert.throws(
		() => validateReleaseSbom(syft, { ...spdx, packages: [] }, cycloneDx, digest, "linux/amd64"),
		/SPDX output omitted/,
	);
	assert.throws(
		() => validateReleaseSbom(syft, spdx, { ...cycloneDx, components: [] }, digest, "linux/amd64"),
		/CycloneDX output omitted/,
	);
});

await test("records unknown licenses instead of pretending they are known", () => {
	const result = validateReleaseSbom(
		{ ...syft, artifacts: [{ ...artifact, licenses: [] }] },
		spdx,
		cycloneDx,
		digest,
		"linux/amd64",
	);
	assert.equal(result.packagesWithLicense, 0);
	assert.deepEqual(result.packagesWithoutLicense, [{ name: "example", version: "1.2.3" }]);
});
