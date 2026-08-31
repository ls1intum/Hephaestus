import { readFileSync, writeFileSync } from "node:fs";

type JsonObject = Record<string, unknown>;

function isJsonObject(value: unknown): value is JsonObject {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

function object(value: unknown, label: string): JsonObject {
	if (!isJsonObject(value)) throw new Error(`${label} must be an object`);
	return value;
}

function array(value: unknown, label: string): unknown[] {
	if (!Array.isArray(value)) throw new Error(`${label} must be an array`);
	return value;
}

function text(value: unknown, label: string): string {
	if (typeof value !== "string" || value.length === 0) throw new Error(`${label} must be a string`);
	return value;
}

function packageKey(value: unknown, label: string): string {
	const item = object(value, label);
	return `${text(item.name, `${label}.name`)}\u0000${text(item.version, `${label}.version`)}`;
}

function purl(value: unknown): string | undefined {
	const item = object(value, "package");
	return typeof item.purl === "string" && item.purl.length > 0 ? item.purl : undefined;
}

export function validateReleaseSbom(
	syftInput: unknown,
	spdxInput: unknown,
	cycloneDxInput: unknown,
	digest: string,
	platform: string,
): JsonObject {
	if (!/^sha256:[a-f0-9]{64}$/.test(digest)) throw new Error("subject digest is malformed");
	const [os, architecture] = platform.split("/");
	if (os !== "linux" || !architecture) throw new Error("platform must be linux/<architecture>");

	const syft = object(syftInput, "Syft SBOM");
	const source = object(syft.source, "Syft source");
	const metadata = object(source.metadata, "Syft source metadata");
	if (source.type !== "image") throw new Error("Syft source is not an image");
	if (metadata.manifestDigest !== digest)
		throw new Error("Syft SBOM is bound to the wrong manifest");
	if (metadata.os !== os || metadata.architecture !== architecture)
		throw new Error("Syft SBOM is bound to the wrong platform");

	const artifacts = array(syft.artifacts, "Syft artifacts");
	if (artifacts.length === 0) throw new Error("Syft SBOM contains no packages");
	const expectedPurls = new Set<string>();
	const missingLicenses: JsonObject[] = [];
	for (const [index, value] of artifacts.entries()) {
		const artifact = object(value, `Syft artifact ${index}`);
		text(artifact.type, `Syft artifact ${index}.type`);
		packageKey(artifact, `Syft artifact ${index}`);
		const artifactPurl = purl(artifact);
		if (artifactPurl) expectedPurls.add(artifactPurl);
		if (array(artifact.locations, `Syft artifact ${index}.locations`).length === 0)
			throw new Error(`Syft artifact ${index} has no location evidence`);
		if (array(artifact.licenses, `Syft artifact ${index}.licenses`).length === 0)
			missingLicenses.push({
				name: text(artifact.name, "artifact.name"),
				version: text(artifact.version, "artifact.version"),
			});
	}

	const spdx = object(spdxInput, "SPDX SBOM");
	if (spdx.spdxVersion !== "SPDX-2.3" || spdx.dataLicense !== "CC0-1.0")
		throw new Error("invalid SPDX document metadata");
	text(spdx.documentNamespace, "SPDX document namespace");
	const spdxPurls = new Set<string>();
	const spdxKeys = new Set<string>();
	for (const [index, value] of array(spdx.packages, "SPDX packages").entries()) {
		const item = object(value, `SPDX package ${index}`);
		if (typeof item.name === "string" && typeof item.versionInfo === "string")
			spdxKeys.add(`${item.name}\u0000${item.versionInfo}`);
		for (const ref of Array.isArray(item.externalRefs) ? item.externalRefs : []) {
			const external = object(ref, "SPDX external reference");
			if (external.referenceType === "purl" && typeof external.referenceLocator === "string")
				spdxPurls.add(external.referenceLocator);
		}
	}

	const cycloneDx = object(cycloneDxInput, "CycloneDX SBOM");
	if (cycloneDx.bomFormat !== "CycloneDX") throw new Error("invalid CycloneDX format");
	text(cycloneDx.specVersion, "CycloneDX spec version");
	const cyclonePurls = new Set<string>();
	const cycloneKeys = new Set<string>();
	for (const [index, value] of array(cycloneDx.components, "CycloneDX components").entries()) {
		const component = object(value, `CycloneDX component ${index}`);
		if (typeof component.name === "string" && typeof component.version === "string")
			cycloneKeys.add(`${component.name}\u0000${component.version}`);
		if (typeof component.purl === "string") cyclonePurls.add(component.purl);
	}

	for (const value of artifacts) {
		const artifactPurl = purl(value);
		const key = packageKey(value, "Syft artifact");
		if (!(artifactPurl ? spdxPurls.has(artifactPurl) : spdxKeys.has(key)))
			throw new Error(`SPDX output omitted ${key.replace("\u0000", "@")} from the Syft inventory`);
		if (!(artifactPurl ? cyclonePurls.has(artifactPurl) : cycloneKeys.has(key)))
			throw new Error(
				`CycloneDX output omitted ${key.replace("\u0000", "@")} from the Syft inventory`,
			);
	}

	return {
		schemaVersion: 1,
		digest,
		platform,
		packageCount: artifacts.length,
		packagesWithPurl: expectedPurls.size,
		packagesWithLicense: artifacts.length - missingLicenses.length,
		packagesWithoutLicense: missingLicenses,
	};
}

if (import.meta.main) {
	const [syftPath, spdxPath, cycloneDxPath, digest, platform, outputPath] = process.argv.slice(2);
	if (!syftPath || !spdxPath || !cycloneDxPath || !digest || !platform || !outputPath)
		throw new Error(
			"usage: check-release-sbom <syft> <spdx> <cyclonedx> <digest> <platform> <output>",
		);
	const readJson = (path: string): unknown => JSON.parse(readFileSync(path, "utf8")) as unknown;
	writeFileSync(
		outputPath,
		`${JSON.stringify(validateReleaseSbom(readJson(syftPath), readJson(spdxPath), readJson(cycloneDxPath), digest, platform), null, 2)}\n`,
	);
}
