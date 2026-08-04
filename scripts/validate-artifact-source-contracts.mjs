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

const requireDescription = (value, label) => {
	if (typeof value?.description !== "string" || value.description.trim() === "") {
		throw new Error(`${label} needs a description`);
	}
};

const validateSchemaDocumentation = (schema, label) => {
	if (typeof schema.title !== "string" || schema.title.trim() === "") {
		throw new Error(`${label} needs a title`);
	}
	requireDescription(schema, label);
	for (const [name, property] of Object.entries(schema.properties ?? {})) {
		requireDescription(property, `${label} property '${name}'`);
	}
	for (const [name, definition] of Object.entries(schema.$defs ?? {})) {
		requireDescription(definition, `${label} definition '${name}'`);
		for (const [propertyName, property] of Object.entries(definition.properties ?? {})) {
			requireDescription(property, `${label} definition '${name}.${propertyName}'`);
		}
	}
	const arrayItem = schema.properties?.decisions?.items;
	if (arrayItem?.properties) {
		requireDescription(arrayItem, `${label} decision`);
		for (const [name, property] of Object.entries(arrayItem.properties)) {
			requireDescription(property, `${label} decision property '${name}'`);
		}
	}
};

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

for (const version of contractVersions) {
	const versionDir = path.join(contractsRoot, version);
	for (const file of await readdir(versionDir)) {
		if (file.endsWith(".schema.json")) {
			const schema = await readJson(path.join(versionDir, file));
			validateSchemaDocumentation(schema, `${version}/${file}`);
			ajv.addSchema(schema);
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
			decision.sourceChecks,
			"sourceKind",
			`${label} practice '${decision.practiceSlug}' source check`,
		);
		if (new Set(decision.reasonCodes).size !== decision.reasonCodes.length) {
			throw new Error(`${label} practice '${decision.practiceSlug}' repeats a reason code`);
		}
		for (const sourceCheck of decision.sourceChecks) {
			if (new Set(sourceCheck.reasonCodes).size !== sourceCheck.reasonCodes.length) {
				throw new Error(`${label} source check '${sourceCheck.sourceKind}' repeats a reason code`);
			}
			if (sourceCheck.meetsRequirements !== (sourceCheck.reasonCodes.length === 0)) {
				throw new Error(`${label} source check '${sourceCheck.sourceKind}' has inconsistent reason codes`);
			}
		}
		if (
			decision.ready !==
			(decision.reasonCodes.length === 0 &&
				decision.sourceChecks.every((sourceCheck) => sourceCheck.meetsRequirements))
		) {
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
		"automated-assessment-readiness-report.schema.json",
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
	const sourceUsePurposes = new Set([
		"AUTOMATED_PRACTICE_ASSESSMENT",
		"PRACTICE_FEEDBACK_DELIVERY",
		"CONVERSATIONAL_MENTORING",
		"OPERATOR_EVIDENCE_REVIEW",
	]);
	for (const source of catalog.sources) {
		const covered = new Set();
		for (const decisionId of source.useDecisionIds) {
			const decision = decisions.get(decisionId);
			if (!decision) throw new Error(`${version} source '${source.kind}' references unknown decision '${decisionId}'`);
			if (decision.sourceKind !== source.kind || decision.retentionPolicy !== source.retentionPolicy || decision.erasurePolicy !== source.erasurePolicy) {
				throw new Error(`${version} decision '${decision.id}' does not match source '${source.kind}'`);
			}
			if (covered.has(decision.purpose) || !sourceUsePurposes.has(decision.purpose)) {
				throw new Error(`${version} decision '${decision.id}' has an invalid or duplicate purpose`);
			}
			covered.add(decision.purpose);
		}
		if (covered.size !== sourceUsePurposes.size) throw new Error(`${version} source '${source.kind}' lacks a product-use decision`);
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

const validateEvidenceSemantics = (requirements, label) => {
	const profile = profiles.get(requirements.evidenceProfile);
	if (!profile) throw new Error(`${label} references unknown profile '${requirements.evidenceProfile}'`);
	const requiredKinds = new Set();
	const optionalKinds = new Set();
	for (const requirement of requirements.requiredEvidence) {
		const source = sources.get(requirement.sourceKind);
		if (!source) throw new Error(`${label} references unknown source '${requirement.sourceKind}'`);
		if (!profile.allowedSources.includes(requirement.sourceKind))
			throw new Error(`${label} references source outside profile '${requirement.sourceKind}'`);
		if (requiredKinds.has(requirement.sourceKind))
			throw new Error(`${label} duplicates '${requirement.sourceKind}'`);
		requiredKinds.add(requirement.sourceKind);
		if (requirement.completeness === "COMPLETE" && !source.completeness.supportsComplete)
			throw new Error(`${label} requires impossible COMPLETE evidence from '${requirement.sourceKind}'`);
		if (requirement.freshness === "CURRENT" && source.freshness.mode === "NOT_APPLICABLE")
			throw new Error(`${label} requires impossible CURRENT evidence from '${requirement.sourceKind}'`);
	}
	for (const requirement of requirements.optionalContext) {
		if (!sources.has(requirement.sourceKind))
			throw new Error(`${label} references unknown source '${requirement.sourceKind}'`);
		if (!profile.allowedSources.includes(requirement.sourceKind))
			throw new Error(`${label} references source outside profile '${requirement.sourceKind}'`);
		if (optionalKinds.has(requirement.sourceKind))
			throw new Error(`${label} duplicates '${requirement.sourceKind}'`);
		if (requiredKinds.has(requirement.sourceKind))
			throw new Error(`${label} makes '${requirement.sourceKind}' both required and optional`);
		optionalKinds.add(requirement.sourceKind);
	}
};

for (const [name, requirements] of Object.entries(practiceCatalog.automatedAssessmentPolicy)) {
	validate(
		"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/practice-automated-assessment-policy.schema.json",
		requirements,
		`default-catalog.json automatedAssessmentPolicy.${name}`,
	);
	validateEvidenceSemantics(requirements, `default-catalog.json automatedAssessmentPolicy.${name}`);
}

const evidenceSchema =
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/practice-automated-assessment-policy.schema.json";
const invalidOptional = structuredClone(Object.values(practiceCatalog.automatedAssessmentPolicy)[0]);
invalidOptional.optionalContext = [
	{
		sourceKind: invalidOptional.requiredEvidence[0].sourceKind,
		completeness: "COMPLETE",
		freshness: "CURRENT",
	},
];
if (ajv.validate(evidenceSchema, invalidOptional)) {
	throw new Error("practice evidence schema accepted quality constraints on an optional source");
}

const unexplainedAdditionalContext = structuredClone(Object.values(practiceCatalog.automatedAssessmentPolicy)[0]);
unexplainedAdditionalContext.automatedAssessment.evidenceSufficiency = "DECLARED_EVIDENCE_INSUFFICIENT";
unexplainedAdditionalContext.knownLimitations = [];
if (ajv.validate(evidenceSchema, unexplainedAdditionalContext)) {
	throw new Error("practice evidence schema accepted unexplained additional context");
}

const withoutAssessment = structuredClone(Object.values(practiceCatalog.automatedAssessmentPolicy)[0]);
withoutAssessment.automatedAssessment = { mode: "NONE", evidenceSufficiency: "NONE" };
withoutAssessment.requiredEvidence = [];
withoutAssessment.optionalContext = [];
withoutAssessment.knownLimitations = [];
validate(evidenceSchema, withoutAssessment, "valid no-assessment requirements fixture");

const expectSchemaRejection = (label, mutate) => {
	const requirements = structuredClone(Object.values(practiceCatalog.automatedAssessmentPolicy)[0]);
	mutate(requirements);
	if (ajv.validate(evidenceSchema, requirements)) {
		throw new Error(`practice evidence schema accepted ${label}`);
	}
};

expectSchemaRejection("legacy observability property", (requirements) => {
	requirements.observability = "LANGUAGE_MODEL";
	delete requirements.automatedAssessment;
});
for (const [legacyName, currentName] of [
	["profile", "evidenceProfile"],
	["required", "requiredEvidence"],
	["optional", "optionalContext"],
	["detectorCapability", "automatedAssessment"],
	["onUnsatisfied", "whenEvidenceIsInsufficient"],
	["blindSpots", "knownLimitations"],
]) {
	expectSchemaRejection(`legacy '${legacyName}' property`, (requirements) => {
		requirements[legacyName] = requirements[currentName];
		delete requirements[currentName];
	});
}
expectSchemaRejection("legacy limitation summary", (requirements) => {
	requirements.knownLimitations[0].summary = requirements.knownLimitations[0].description;
	delete requirements.knownLimitations[0].description;
});
expectSchemaRejection("legacy semantic assessment mode", (requirements) => {
	requirements.automatedAssessment.mode = "SEMANTIC";
});
expectSchemaRejection("incoherent automated assessment", (requirements) => {
	requirements.automatedAssessment = {
		mode: "NONE",
		evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
	};
});
expectSchemaRejection("assessment evidence on a no-automated-assessment practice", (requirements) => {
	requirements.automatedAssessment = { mode: "NONE", evidenceSufficiency: "NONE" };
});
expectSchemaRejection("assessment without required evidence", (requirements) => {
	requirements.requiredEvidence = [];
});

const expectSemanticRejection = (mutate, expected) => {
	const requirements = structuredClone(Object.values(practiceCatalog.automatedAssessmentPolicy)[0]);
	mutate(requirements);
	try {
		validateEvidenceSemantics(requirements, "adversarial requirements");
	} catch (error) {
		if (String(error).includes(expected)) return;
		throw error;
	}
	throw new Error(`semantic validator accepted ${expected}`);
};

expectSemanticRejection((d) => (d.requiredEvidence[0].sourceKind = "scm.unknown"), "unknown source");
expectSemanticRejection((d) => (d.requiredEvidence[0].sourceKind = "scm.issue.core"), "outside profile");
expectSemanticRejection((d) => d.requiredEvidence.push(structuredClone(d.requiredEvidence[0])), "duplicates");
expectSemanticRejection((requirements) =>
	requirements.optionalContext.push({ sourceKind: requirements.requiredEvidence[0].sourceKind }), "both required and optional");
expectSemanticRejection((d) => {
	d.requiredEvidence[0] = { sourceKind: "outline.documents", completeness: "COMPLETE", freshness: "NO_REQUIREMENT" };
}, "impossible COMPLETE");
expectSemanticRejection((d) => {
	d.requiredEvidence[0] = { sourceKind: "scm.pull-request.comments", completeness: "NO_REQUIREMENT", freshness: "CURRENT" };
}, "impossible CURRENT");

const manifestSchema =
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/artifact-source-manifest.schema.json";
const manifest = {
	contractVersion: "1.0.0",
	catalogDigest: sourceCatalogDigest,
	evidenceProfile: "pull-request-review",
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
	["unknown profile", { ...manifest, evidenceProfile: "unknown-review" }],
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
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/automated-assessment-readiness-report.schema.json";
const sourceCheck = {
	sourceKind: "scm.pull-request.diff",
	sourceContractVersion: "1.0.0",
	checkedAt: "2026-08-03T00:00:00Z",
	temporalAnchor: "2026-08-03T00:00:00Z",
	freshness: "CURRENT",
	meetsRequirements: true,
	reasonCodes: [],
};
const readiness = {
	contractVersion: "1.0.0",
	catalogDigest: sourceCatalogDigest,
	evidenceProfile: "pull-request-review",
	manifestCapturedAt: "2026-08-03T00:00:00Z",
	decidedAt: "2026-08-03T00:00:00Z",
	decisions: [
		{
			practiceSlug: "example",
			decidedAt: "2026-08-03T00:00:00Z",
			ready: true,
			reasonCodes: [],
			sourceChecks: [sourceCheck],
		},
	],
};
validate(readinessSchema, readiness, "valid readiness fixture");
validateReadinessSemantics(readiness, "valid readiness fixture");
const skippedAssessment = {
	...readiness,
	decisions: [
		{
			...readiness.decisions[0],
			ready: false,
			reasonCodes: ["NO_AUTOMATED_ASSESSMENT"],
			sourceChecks: [],
		},
	],
};
validate(readinessSchema, skippedAssessment, "valid skipped-assessment fixture");
validateReadinessSemantics(skippedAssessment, "valid skipped-assessment fixture");
for (const [label, decision] of [
	["zero source checks", { ...readiness.decisions[0], sourceChecks: [] }],
	["duplicate source check", { ...readiness.decisions[0], sourceChecks: [sourceCheck, structuredClone(sourceCheck)] }],
	["ready with failed source check", { ...readiness.decisions[0], sourceChecks: [{ ...sourceCheck, meetsRequirements: false, reasonCodes: ["SOURCE_NOT_CURRENT"] }] }],
	["skipped without reason", { ...readiness.decisions[0], ready: false }],
	["rejection without reason", { ...readiness.decisions[0], ready: false, sourceChecks: [{ ...sourceCheck, meetsRequirements: false }] }],
]) {
	if (ajv.validate(readinessSchema, { ...readiness, decisions: [decision] }))
		throw new Error(`readiness schema accepted ${label}`);
}
const wrongProfileSourceCheck = {
	...readiness,
	decisions: [{ ...readiness.decisions[0], sourceChecks: [{ ...sourceCheck, sourceKind: "scm.issue.core" }] }],
};
if (ajv.validate(readinessSchema, wrongProfileSourceCheck)) {
	throw new Error("readiness schema accepted a source check outside the selected profile");
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
