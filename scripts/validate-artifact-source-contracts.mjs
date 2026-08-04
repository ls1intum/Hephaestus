import { createHash } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const contractsRoot = path.join(
	root,
	"server/src/main/resources/contracts/artifact-source",
);
const contractVersions = (await readdir(contractsRoot, { withFileTypes: true }))
	.filter((entry) => entry.isDirectory())
	.map((entry) => entry.name)
	.sort();
if (contractVersions.length === 0) throw new Error("No artifact-source contract versions found");
const readJson = async (file) => JSON.parse(await readFile(file, "utf8"));

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

for (const version of contractVersions) {
	const versionDir = path.join(contractsRoot, version);
	for (const file of await readdir(versionDir)) {
		if (file.endsWith(".schema.json")) {
			ajv.addSchema(await readJson(path.join(versionDir, file)));
		}
	}
}

const validate = (schemaId, value, label) => {
	if (!ajv.validate(schemaId, value)) {
		throw new Error(`${label} violates ${schemaId}: ${ajv.errorsText(ajv.errors)}`);
	}
};

const rejectDuplicateProperty = (values, property, label) => {
	const seen = new Set();
	for (const value of values) {
		if (seen.has(value[property])) throw new Error(`${label} duplicates '${value[property]}'`);
		seen.add(value[property]);
	}
};

const validateManifestSemantics = (value, label) => {
	rejectDuplicateProperty(value.sources, "kind", `${label} source`);
	for (const source of value.sources) {
		rejectDuplicateProperty(source.artifacts, "path", `${label} source '${source.kind}' artifact path`);
		if (source.state.availability !== "AVAILABLE" && source.artifacts.length > 0) {
			throw new Error(`${label} unavailable source '${source.kind}' contains artifacts`);
		}
		if (
			source.state.availability === "AVAILABLE" &&
			source.state.content === "NON_EMPTY" &&
			source.artifacts.length === 0
		) {
			throw new Error(`${label} non-empty source '${source.kind}' contains no artifacts`);
		}
	}
};

const validateReadinessSemantics = (value, label) => {
	rejectDuplicateProperty(value.decisions, "practiceSlug", `${label} practice`);
	for (const decision of value.decisions) {
		if (decision.decidedAt !== value.decidedAt) {
			throw new Error(`${label} practice '${decision.practiceSlug}' uses a different decision time`);
		}
		rejectDuplicateProperty(
			decision.assessments,
			"kind",
			`${label} practice '${decision.practiceSlug}' assessment`,
		);
		for (const assessment of decision.assessments) {
			if (new Set(assessment.reasonCodes).size !== assessment.reasonCodes.length) {
				throw new Error(`${label} assessment '${assessment.kind}' repeats a reason code`);
			}
			if (assessment.acceptable !== (assessment.reasonCodes.length === 0)) {
				throw new Error(`${label} assessment '${assessment.kind}' has inconsistent reason codes`);
			}
		}
		if (decision.ready !== decision.assessments.every((assessment) => assessment.acceptable)) {
			throw new Error(`${label} practice '${decision.practiceSlug}' has an inconsistent ready outcome`);
		}
	}
};

const catalogDigests = new Map();

const validateContractVersion = async (version) => {
	const versionDir = path.join(contractsRoot, version);
	const catalogBytes = await readFile(path.join(versionDir, "catalog.json"));
	const catalog = JSON.parse(catalogBytes);
	const catalogDigest = createHash("sha256").update(catalogBytes).digest("hex");
	for (const schemaFile of [
		"artifact-source-manifest.schema.json",
		"practice-readiness-report.schema.json",
	]) {
		const schema = await readJson(path.join(versionDir, schemaFile));
		if (schema.properties.catalogDigest.const !== catalogDigest) {
			throw new Error(`${version}/${schemaFile} does not pin the catalog digest`);
		}
	}
	catalogDigests.set(version, catalogDigest);
	const decisionCatalog = await readJson(path.join(versionDir, "source-use-decisions.json"));
	validate(
		`https://hephaestus.aet.cit.tum.de/contracts/artifact-source/${version}/artifact-source-catalog.schema.json`,
		catalog,
		`${version}/catalog.json`,
	);
	validate(
		`https://hephaestus.aet.cit.tum.de/contracts/artifact-source/${version}/source-use-decisions.schema.json`,
		decisionCatalog,
		`${version}/source-use-decisions.json`,
	);

	rejectDuplicateProperty(catalog.sources, "kind", `${version} source kind`);
	rejectDuplicateProperty(catalog.profiles, "id", `${version} profile`);
	rejectDuplicateProperty(decisionCatalog.decisions, "id", `${version} source-use decision`);

	const versionSources = new Map(catalog.sources.map((source) => [source.kind, source]));
	const decisions = new Map(decisionCatalog.decisions.map((decision) => [decision.id, decision]));
	for (const profile of catalog.profiles) {
		for (const kind of profile.allowedSources) {
			const source = versionSources.get(kind);
			if (!source) throw new Error(`${version} profile '${profile.id}' references unknown source '${kind}'`);
			if (!source.artifactTypes.includes(profile.artifactType)) {
				throw new Error(`${version} profile '${profile.id}' uses '${kind}' for an unsupported artifact type`);
			}
		}
	}
	const purposeByAudience = new Map([
		["PRACTICE_DETECTION", "practice-detection"],
		["PRACTICE_FEEDBACK_RECIPIENTS", "practice-feedback"],
		["PRACTICE_MENTORING", "practice-mentoring"],
		["OPERATOR_QUALITY_ASSURANCE", "operator-quality-assurance"],
	]);
	for (const source of catalog.sources) {
		const covered = new Set();
		for (const decisionId of source.useDecisionIds) {
			const decision = decisions.get(decisionId);
			if (!decision) throw new Error(`${version} source '${source.kind}' references unknown decision '${decisionId}'`);
			if (decision.source !== source.kind || decision.retentionPolicy !== source.retentionPolicy || decision.erasurePolicy !== source.erasurePolicy) {
				throw new Error(`${version} decision '${decision.id}' does not match source '${source.kind}'`);
			}
			if (decision.audiences.length !== 1) throw new Error(`${version} decision '${decision.id}' must grant one audience`);
			const audience = decision.audiences[0];
			if (covered.has(audience) || decision.purpose !== purposeByAudience.get(audience)) {
				throw new Error(`${version} decision '${decision.id}' has an invalid audience-purpose grant`);
			}
			covered.add(audience);
		}
		if (covered.size !== purposeByAudience.size) throw new Error(`${version} source '${source.kind}' lacks a product audience decision`);
	}
	const referencedDecisions = new Set(catalog.sources.flatMap((source) => source.useDecisionIds));
	for (const decision of decisionCatalog.decisions) {
		if (!referencedDecisions.has(decision.id)) {
			throw new Error(`${version} decision '${decision.id}' is not referenced by a source`);
		}
	}
	return catalog;
};

const catalogs = new Map();
for (const version of contractVersions) catalogs.set(version, await validateContractVersion(version));

const sourceCatalog = catalogs.get("1.0.0");
const sourceCatalogDigest = catalogDigests.get("1.0.0");
const practiceCatalog = await readJson(
	path.join(root, "server/src/main/resources/practices/default-catalog.json"),
);
const sources = new Map(sourceCatalog.sources.map((source) => [source.kind, source]));
const profiles = new Map(sourceCatalog.profiles.map((profile) => [profile.id, profile]));

const validateEvidenceSemantics = (declaration, label) => {
	const profile = profiles.get(declaration.profile);
	if (!profile) throw new Error(`${label} references unknown profile '${declaration.profile}'`);
	const required = new Set();
	const optional = new Set();
	for (const requirement of declaration.required) {
		const source = sources.get(requirement.sourceKind);
		if (!source) throw new Error(`${label} references unknown source '${requirement.sourceKind}'`);
		if (!profile.allowedSources.includes(requirement.sourceKind))
			throw new Error(`${label} references source outside profile '${requirement.sourceKind}'`);
		if (required.has(requirement.sourceKind)) throw new Error(`${label} duplicates '${requirement.sourceKind}'`);
		required.add(requirement.sourceKind);
		if (requirement.completeness === "COMPLETE" && !source.completeness.supportsComplete)
			throw new Error(`${label} requires impossible COMPLETE evidence from '${requirement.sourceKind}'`);
		if (requirement.freshness === "CURRENT" && source.freshness.mode === "NOT_APPLICABLE")
			throw new Error(`${label} requires impossible CURRENT evidence from '${requirement.sourceKind}'`);
	}
	for (const requirement of declaration.optional) {
		if (!sources.has(requirement.sourceKind))
			throw new Error(`${label} references unknown source '${requirement.sourceKind}'`);
		if (!profile.allowedSources.includes(requirement.sourceKind))
			throw new Error(`${label} references source outside profile '${requirement.sourceKind}'`);
		if (optional.has(requirement.sourceKind)) throw new Error(`${label} duplicates '${requirement.sourceKind}'`);
		if (required.has(requirement.sourceKind))
			throw new Error(`${label} makes '${requirement.sourceKind}' both required and optional`);
		optional.add(requirement.sourceKind);
	}
};

for (const [name, declaration] of Object.entries(practiceCatalog.evidenceDeclarations)) {
	validate(
		"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/practice-evidence-declaration.schema.json",
		declaration,
		`default-catalog.json evidenceDeclarations.${name}`,
	);
	validateEvidenceSemantics(declaration, `default-catalog.json evidenceDeclarations.${name}`);
}

const evidenceSchema =
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/practice-evidence-declaration.schema.json";
const invalidOptional = structuredClone(Object.values(practiceCatalog.evidenceDeclarations)[0]);
invalidOptional.optional = [
	{
		sourceKind: invalidOptional.required[0].sourceKind,
		completeness: "COMPLETE",
		freshness: "CURRENT",
	},
];
if (ajv.validate(evidenceSchema, invalidOptional)) {
	throw new Error("practice evidence schema accepted quality constraints on an optional source");
}

const expectSemanticRejection = (mutate, expected) => {
	const declaration = structuredClone(Object.values(practiceCatalog.evidenceDeclarations)[0]);
	mutate(declaration);
	try {
		validateEvidenceSemantics(declaration, "adversarial declaration");
	} catch (error) {
		if (String(error).includes(expected)) return;
		throw error;
	}
	throw new Error(`semantic validator accepted ${expected}`);
};

expectSemanticRejection((d) => (d.required[0].sourceKind = "scm.unknown"), "unknown source");
expectSemanticRejection((d) => (d.required[0].sourceKind = "scm.issue.core"), "outside profile");
expectSemanticRejection((d) => d.required.push(structuredClone(d.required[0])), "duplicates");
expectSemanticRejection((d) => d.optional.push({ sourceKind: d.required[0].sourceKind, completeness: "ANY", freshness: "ANY" }), "both required and optional");
expectSemanticRejection((d) => {
	d.required[0] = { sourceKind: "outline.documents", completeness: "COMPLETE", freshness: "ANY" };
}, "impossible COMPLETE");
expectSemanticRejection((d) => {
	d.required[0] = { sourceKind: "scm.pull-request.comments", completeness: "ANY", freshness: "CURRENT" };
}, "impossible CURRENT");

const manifestSchema =
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/artifact-source-manifest.schema.json";
const manifest = {
	contractVersion: "1.0.0",
	catalogDigest: sourceCatalogDigest,
	profileId: "pull-request-review",
	capturedAt: "2026-08-03T00:00:00Z",
	sources: profiles.get("pull-request-review").allowedSources.map((kind) => ({
		kind,
		state:
			kind === "scm.pull-request.diff"
				? {
						availability: "AVAILABLE",
						content: "EMPTY",
						completeness: "COMPLETE",
						facts: {
							capturedAt: "2026-08-03T00:00:00Z",
							queryScope: "one pinned diff",
							completenessBasis: "IMMUTABLE_OBJECT",
							representationFidelity: "EXACT",
						},
					}
				: { availability: "NOT_COLLECTED", reasonCode: "MINIMIZED" },
		artifacts: [],
	})),
	viewTransformations: [],
};
validate(manifestSchema, manifest, "valid manifest fixture");
validateManifestSemantics(manifest, "valid manifest fixture");
for (const [label, invalid] of [
	["empty source list", { ...manifest, sources: [] }],
	["unknown source kind", { ...manifest, sources: [{ ...manifest.sources[0], kind: "scm.unknown.source" }, ...manifest.sources.slice(1)] }],
	["unknown profile", { ...manifest, profileId: "unknown-review" }],
	["duplicate source capture", { ...manifest, sources: [...manifest.sources, structuredClone(manifest.sources[0])] }],
	["wrong catalog digest", { ...manifest, catalogDigest: "a".repeat(64) }],
	["unsafe artifact path", {
		...manifest,
		sources: [{ ...manifest.sources[0], state: { ...manifest.sources[0].state, content: "NON_EMPTY" }, artifacts: [{ path: "../secret", mediaType: "text/plain", sha256: "b".repeat(64), bytes: 1 }] }, ...manifest.sources.slice(1)],
	}],
	["non-canonical artifact path", {
		...manifest,
		sources: [{ ...manifest.sources[0], state: { ...manifest.sources[0].state, content: "NON_EMPTY" }, artifacts: [{ path: "./context.json", mediaType: "application/json", sha256: "b".repeat(64), bytes: 1 }] }, ...manifest.sources.slice(1)],
	}],
]) {
	if (ajv.validate(manifestSchema, invalid)) throw new Error(`manifest schema accepted ${label}`);
}
const duplicateArtifactPath = structuredClone(manifest);
duplicateArtifactPath.sources[0].artifacts = [
	{ path: "context.json", mediaType: "application/json", sha256: "b".repeat(64), bytes: 1 },
	{ path: "context.json", mediaType: "application/json", sha256: "c".repeat(64), bytes: 2 },
];
try {
	validateManifestSemantics(duplicateArtifactPath, "adversarial manifest");
	throw new Error("manifest semantic validator accepted duplicate artifact paths");
} catch (error) {
	if (!String(error).includes("duplicates 'context.json'")) throw error;
}

const readinessSchema =
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/practice-readiness-report.schema.json";
const assessment = {
	kind: "scm.pull-request.diff",
	policyVersion: "1.0.0",
	assessedAt: "2026-08-03T00:00:00Z",
	temporalAnchor: "2026-08-03T00:00:00Z",
	freshness: "CURRENT",
	acceptable: true,
	reasonCodes: [],
};
const readiness = {
	contractVersion: "1.0.0",
	catalogDigest: sourceCatalogDigest,
	profileId: "pull-request-review",
	manifestCapturedAt: "2026-08-03T00:00:00Z",
	decidedAt: "2026-08-03T00:00:00Z",
	decisions: [{ practiceSlug: "example", decidedAt: "2026-08-03T00:00:00Z", ready: true, assessments: [assessment] }],
};
validate(readinessSchema, readiness, "valid readiness fixture");
validateReadinessSemantics(readiness, "valid readiness fixture");
for (const [label, decision] of [
	["zero assessments", { ...readiness.decisions[0], assessments: [] }],
	["duplicate assessment", { ...readiness.decisions[0], assessments: [assessment, structuredClone(assessment)] }],
	["ready with rejection", { ...readiness.decisions[0], assessments: [{ ...assessment, acceptable: false, reasonCodes: ["STALE"] }] }],
	["refused without rejection", { ...readiness.decisions[0], ready: false }],
	["rejection without reason", { ...readiness.decisions[0], ready: false, assessments: [{ ...assessment, acceptable: false }] }],
]) {
	if (ajv.validate(readinessSchema, { ...readiness, decisions: [decision] }))
		throw new Error(`readiness schema accepted ${label}`);
}
const wrongProfileAssessment = {
	...readiness,
	decisions: [{ ...readiness.decisions[0], assessments: [{ ...assessment, kind: "scm.issue.core" }] }],
};
if (ajv.validate(readinessSchema, wrongProfileAssessment)) {
	throw new Error("readiness schema accepted an assessment outside the selected profile");
}
const duplicatePractice = {
	...readiness,
	decisions: [readiness.decisions[0], { ...structuredClone(readiness.decisions[0]), decidedAt: "2026-08-03T00:00:01Z" }],
};
try {
	validateReadinessSemantics(duplicatePractice, "adversarial readiness");
	throw new Error("readiness semantic validator accepted duplicate practices");
} catch (error) {
	if (!String(error).includes("duplicates 'example'")) throw error;
}
const inconsistentDecisionTime = structuredClone(readiness);
inconsistentDecisionTime.decisions[0].decidedAt = "2026-08-03T00:00:01Z";
try {
	validateReadinessSemantics(inconsistentDecisionTime, "adversarial readiness");
	throw new Error("readiness semantic validator accepted inconsistent decision times");
} catch (error) {
	if (!String(error).includes("different decision time")) throw error;
}
