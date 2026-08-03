import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const contractDir = path.join(
	root,
	"server/src/main/resources/contracts/artifact-source/1.0.0",
);
const readJson = async (file) => JSON.parse(await readFile(file, "utf8"));

const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);

for (const file of await readdir(contractDir)) {
	if (file.endsWith(".schema.json")) {
		ajv.addSchema(await readJson(path.join(contractDir, file)));
	}
}

const validate = (schemaId, value, label) => {
	if (!ajv.validate(schemaId, value)) {
		throw new Error(`${label} violates ${schemaId}: ${ajv.errorsText(ajv.errors)}`);
	}
};

validate(
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/artifact-source-catalog.schema.json",
	await readJson(path.join(contractDir, "catalog.json")),
	"catalog.json",
);
validate(
	"https://hephaestus.aet.cit.tum.de/contracts/artifact-source/1.0.0/source-use-decisions.schema.json",
	await readJson(path.join(contractDir, "source-use-decisions.json")),
	"source-use-decisions.json",
);

const sourceCatalog = await readJson(path.join(contractDir, "catalog.json"));
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
	catalogDigest: "a".repeat(64),
	profileId: "pull-request-review",
	capturedAt: "2026-08-03T00:00:00Z",
	sources: [
		{
			kind: "scm.pull-request.diff",
			state: {
				availability: "AVAILABLE",
				content: "EMPTY",
				completeness: "COMPLETE",
				facts: {
					capturedAt: "2026-08-03T00:00:00Z",
					queryScope: "one pinned diff",
					completenessBasis: "IMMUTABLE_OBJECT",
					representationFidelity: "EXACT",
				},
			},
			artifacts: [],
		},
	],
	viewTransformations: [],
};
validate(manifestSchema, manifest, "valid manifest fixture");
for (const [label, invalid] of [
	["empty source list", { ...manifest, sources: [] }],
	["invalid source kind", { ...manifest, sources: [{ ...manifest.sources[0], kind: "INVALID" }] }],
	["unsafe artifact path", {
		...manifest,
		sources: [{ ...manifest.sources[0], state: { ...manifest.sources[0].state, content: "NON_EMPTY" }, artifacts: [{ path: "../secret", mediaType: "text/plain", sha256: "b".repeat(64), bytes: 1 }] }],
	}],
]) {
	if (ajv.validate(manifestSchema, invalid)) throw new Error(`manifest schema accepted ${label}`);
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
	catalogDigest: "a".repeat(64),
	profileId: "pull-request-review",
	manifestCapturedAt: "2026-08-03T00:00:00Z",
	decidedAt: "2026-08-03T00:00:00Z",
	decisions: [{ practiceSlug: "example", decidedAt: "2026-08-03T00:00:00Z", ready: true, assessments: [assessment] }],
};
validate(readinessSchema, readiness, "valid readiness fixture");
for (const [label, decision] of [
	["zero assessments", { ...readiness.decisions[0], assessments: [] }],
	["ready with rejection", { ...readiness.decisions[0], assessments: [{ ...assessment, acceptable: false, reasonCodes: ["STALE"] }] }],
	["refused without rejection", { ...readiness.decisions[0], ready: false }],
	["rejection without reason", { ...readiness.decisions[0], ready: false, assessments: [{ ...assessment, acceptable: false }] }],
]) {
	if (ajv.validate(readinessSchema, { ...readiness, decisions: [decision] }))
		throw new Error(`readiness schema accepted ${label}`);
}
