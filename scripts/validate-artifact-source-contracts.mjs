/**
 * Checks the *published* artifact-source contract: the JSON Schemas, and the two catalogs they describe.
 *
 * What this deliberately does not check: the semantics. Nothing in the JVM reads these schemas — the
 * server parses the same JSON with hand-written Jackson code and enforces every rule itself, against the
 * shipped resources, under unit test: `ArtifactSourceContract` refuses a source whose authority, identity,
 * completeness and required quality disagree; `ClasspathArtifactSourceCatalogRegistry` refuses use
 * decisions that do not cover every purpose; `SourceCapture` refuses an absent source carrying artifacts;
 * `PracticeDefinitionValidator` refuses a practice reading a source that cannot apply to what it reviews,
 * and `BundledPracticeCatalogLoaderTest` runs the shipped catalog through it. Restating any of that here
 * would buy a second opinion that can only drift from the one that decides.
 *
 * So this owns what nothing in the JVM does:
 *
 *   - the schemas compile, are documented, and actually reject what they claim to reject;
 *   - they pin the exact catalog bytes they were written against;
 *   - their per-artifact-kind evidence allow-lists are checked against the catalog rather than trusted
 *     as a copy of it — they are the one place a new source has to be repeated, so they are the one
 *     place it will be forgotten;
 *   - the bundled practice catalog satisfies its own schema, which the loader cannot tell you: Jackson
 *     reads the fields it knows and says nothing about a retired one left behind;
 *   - every precompute script on disk belongs to a practice, and every practice's script is on disk.
 */
import { createHash } from "node:crypto";
import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const contractsRoot = path.join(root, "server/src/main/resources/contracts/artifact-source");
const contractVersions = (await readdir(contractsRoot, { withFileTypes: true }))
	.filter((entry) => entry.isDirectory())
	.map((entry) => entry.name)
	.sort();
if (contractVersions.length === 0) throw new Error("No artifact-source contract versions found");
const readJson = async (file) => JSON.parse(await readFile(file, "utf8"));
const schemaId = (version, file) =>
	`https://hephaestus.aet.cit.tum.de/contracts/artifact-source/${version}/${file}`;

const requireDescription = (value, label) => {
	if (typeof value?.description !== "string" || value.description.trim() === "") {
		throw new Error(`${label} needs a description`);
	}
};

/**
 * Every published name explains itself.
 *
 * A source contract is read by practice authors and by whoever has to decide, months later, whether a
 * field still means what it says. An undocumented property is the first step of the rot this whole
 * contract exists to prevent.
 */
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

const defaultCatalogSchemaPath = path.join(
	root,
	"server/src/main/resources/practices/default-catalog.schema.json",
);
const defaultCatalogSchema = await readJson(defaultCatalogSchemaPath);
validateSchemaDocumentation(defaultCatalogSchema, "practices/default-catalog.schema.json");
ajv.addSchema(defaultCatalogSchema);

const validate = (id, value, label) => {
	if (!ajv.validate(id, value)) {
		throw new Error(`${label} violates ${id}: ${ajv.errorsText(ajv.errors)}`);
	}
};

const rejectDuplicateProperty = (values, property, label) => {
	const seen = new Set();
	for (const value of values) {
		if (seen.has(value[property])) throw new Error(`${label} duplicates '${value[property]}'`);
		seen.add(value[property]);
	}
};

const expectRejection = (id, value, label) => {
	if (ajv.validate(id, value)) throw new Error(`${label}`);
};

const sameSet = (actual, expected) => {
	const left = [...new Set(actual)].sort();
	const right = [...new Set(expected)].sort();
	return left.length === right.length && left.every((value, index) => value === right[index]);
};

/**
 * The schemas that travel with a run repeat the catalog: every source kind, every artifact kind, and
 * which sources may appear for which kind. That repetition is unavoidable — a JSON Schema cannot read
 * another file — so it is checked rather than trusted. A source added to the catalog and forgotten here
 * is a source the runtime can capture and the manifest schema rejects, which surfaces as a failed run
 * long after the commit that caused it.
 */
const validateSchemasTrackTheCatalog = async (version, catalog) => {
	const sourceKinds = catalog.sources.map((source) => source.kind);
	const artifactKinds = [...new Set(catalog.sources.flatMap((source) => source.artifactKinds))];
	const sourcesFor = (artifactKind) =>
		catalog.sources
			.filter((source) => source.artifactKinds.includes(artifactKind))
			.map((source) => source.kind);

	const catalogSchema = await readJson(
		path.join(contractsRoot, version, "artifact-source-catalog.schema.json"),
	);
	// A kind the schema lets a source claim but no source supplies is a kind a practice can be authored
	// against and no review can ever read evidence for.
	const declarable = catalogSchema.$defs.source.properties.artifactKinds.items.enum;
	if (!sameSet(declarable, artifactKinds)) {
		throw new Error(
			`${version} catalog schema allows artifact kinds no source supplies: ${declarable.filter((kind) => !artifactKinds.includes(kind)).join(", ") || "(none)"}`,
		);
	}

	for (const [file, artifactKindPath] of [
		["artifact-source-manifest.schema.json", (schema) => schema.properties.artifactKind.enum],
		[
			"automated-review-readiness-report.schema.json",
			(schema) => schema.properties.artifactKind.enum,
		],
	]) {
		const schema = await readJson(path.join(contractsRoot, version, file));
		if (!sameSet(artifactKindPath(schema), artifactKinds)) {
			throw new Error(`${version}/${file} does not list exactly the catalog's artifact kinds`);
		}
	}

	// Checked through Ajv rather than by walking the schema, so a restructured conditional that still
	// enforces the same allow-list keeps passing and one that quietly stops enforcing it does not.
	const digest = catalogDigests.get(version);
	const manifestId = schemaId(version, "artifact-source-manifest.schema.json");
	const readinessId = schemaId(version, "automated-review-readiness-report.schema.json");
	const capturedAt = "2026-08-03T00:00:00Z";
	const absent = { availability: "NOT_COLLECTED", reasonCode: "NOT_NEEDED_BY_READY_PRACTICES" };
	const manifestOf = (artifactKind, kinds) => ({
		contractVersion: version,
		catalogDigest: digest,
		artifactKind,
		capturedAt,
		sources: kinds.map((kind) => ({ kind, state: absent, artifacts: [] })),
	});
	const readinessOf = (artifactKind, kind) => ({
		contractVersion: version,
		catalogDigest: digest,
		artifactKind,
		manifestCapturedAt: capturedAt,
		decidedAt: capturedAt,
		decisions: [
			{
				practiceSlug: "example",
				decidedAt: capturedAt,
				ready: true,
				reasonCodes: [],
				sourceChecks: [
					{
						sourceKind: kind,
						sourceContractVersion: version,
						checkedAt: capturedAt,
						temporalAnchor: capturedAt,
						meetsRequirements: true,
						reasonCodes: [],
					},
				],
			},
		],
	});

	for (const artifactKind of artifactKinds) {
		const applicable = sourcesFor(artifactKind);
		validate(manifestId, manifestOf(artifactKind, applicable), `${version} ${artifactKind} manifest`);
		// A manifest enumerates every source that could have applied, including the ones it did not
		// collect. Dropping one is how "we chose not to look" becomes indistinguishable from silence.
		expectRejection(
			manifestId,
			manifestOf(artifactKind, applicable.slice(1)),
			`${version}/artifact-source-manifest.schema.json accepted a ${artifactKind} manifest missing '${applicable[0]}'`,
		);
		for (const kind of sourceKinds) {
			const applies = applicable.includes(kind);
			if (!applies) {
				expectRejection(
					manifestId,
					manifestOf(artifactKind, [kind, ...applicable.slice(1)]),
					`${version}/artifact-source-manifest.schema.json accepted '${kind}' for ${artifactKind}`,
				);
			}
			const label = `${version}/automated-review-readiness-report.schema.json`;
			if (applies) {
				validate(readinessId, readinessOf(artifactKind, kind), `${label} ${artifactKind}/${kind}`);
			} else {
				expectRejection(
					readinessId,
					readinessOf(artifactKind, kind),
					`${label} accepted a source check on '${kind}' for ${artifactKind}`,
				);
			}
		}
	}

	// The rejection paths that are not about the allow-list, exercised against the same fixtures.
	const pullRequestKind = artifactKinds.find((kind) => sourcesFor(kind).length > 1) ?? artifactKinds[0];
	const applicable = sourcesFor(pullRequestKind);
	const manifest = manifestOf(pullRequestKind, applicable);
	const available = {
		availability: "AVAILABLE",
		content: "NON_EMPTY",
		completeness: "COMPLETE",
		facts: { capturedAt },
	};
	const artifact = { path: "context.json", mediaType: "application/json", sha256: "b".repeat(64), bytes: 1 };
	const withFirstSource = (state, artifacts) => ({
		...manifest,
		sources: [{ kind: applicable[0], state, artifacts }, ...manifest.sources.slice(1)],
	});
	validate(manifestId, withFirstSource(available, [artifact]), `${version} available-source manifest`);
	for (const [label, invalid] of [
		["an empty source list", { ...manifest, sources: [] }],
		["an unknown source kind", manifestOf(pullRequestKind, ["scm.unknown.source", ...applicable.slice(1)])],
		["a duplicate source capture", { ...manifest, sources: [...manifest.sources, { ...manifest.sources[0] }] }],
		["a wrong catalog digest", { ...manifest, catalogDigest: "a".repeat(64) }],
		["an absent source carrying artifacts", withFirstSource(absent, [artifact])],
		["an escaping artifact path", withFirstSource(available, [{ ...artifact, path: "../secret" }])],
		["a non-canonical artifact path", withFirstSource(available, [{ ...artifact, path: "./context.json" }])],
	]) {
		expectRejection(manifestId, invalid, `${version}/artifact-source-manifest.schema.json accepted ${label}`);
	}

	const readiness = readinessOf(pullRequestKind, applicable[0]);
	const decision = readiness.decisions[0];
	const check = decision.sourceChecks[0];
	const withDecision = (overrides) => ({ ...readiness, decisions: [{ ...decision, ...overrides }] });
	// A practice can be skipped before any source is read at all — nothing to review it with, or the
	// author declaring the evidence insufficient. That says nothing about the sources, so it carries no
	// source checks and must still carry a reason.
	validate(
		readinessId,
		withDecision({ ready: false, reasonCodes: ["NO_AUTOMATED_REVIEW"], sourceChecks: [] }),
		`${version} skipped-practice readiness`,
	);
	for (const [label, overrides] of [
		["a ready decision with no source check", { sourceChecks: [] }],
		["a duplicate source check", { sourceChecks: [check, { ...check }] }],
		[
			"a ready decision over a failed source check",
			{ sourceChecks: [{ ...check, meetsRequirements: false, reasonCodes: ["SOURCE_NOT_AVAILABLE"] }] },
		],
		["a skipped practice with no reason", { ready: false }],
		["a failed source check with no reason", { ready: false, sourceChecks: [{ ...check, meetsRequirements: false }] }],
		["a passing source check carrying a reason", { sourceChecks: [{ ...check, reasonCodes: ["SOURCE_INCOMPLETE"] }] }],
	]) {
		expectRejection(
			readinessId,
			withDecision(overrides),
			`${version}/automated-review-readiness-report.schema.json accepted ${label}`,
		);
	}
};

/**
 * The policy schema describes a shape that only ever exists in memory and in the database, so nothing
 * on disk exercises it. These fixtures are what keeps it honest; `PracticeAutomatedReviewPolicySchemaTest`
 * is what keeps it in step with the record it describes.
 */
const validatePolicySchema = (version) => {
	const id = schemaId(version, "practice-automated-review-policy.schema.json");
	const reviewed = {
		sourceContractVersion: version,
		automatedReview: { mode: "LANGUAGE_MODEL", evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET" },
		whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
		knownLimitations: [
			{ code: "RUNTIME_BEHAVIOR_NOT_OBSERVED", description: "Repository evidence does not establish runtime behaviour." },
		],
	};
	const reason = { code: "NEEDS_A_PERSON", description: "Judging this needs context no source carries." };
	validate(id, reviewed, `${version} reviewed-practice policy`);
	validate(
		id,
		{ ...reviewed, automatedReview: { ...reviewed.automatedReview, evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT" }, insufficiencyReason: reason },
		`${version} declared-insufficient policy`,
	);
	validate(
		id,
		{ ...reviewed, automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" }, knownLimitations: [] },
		`${version} human-only policy`,
	);
	for (const [label, invalid] of [
		["a review mode with no sufficiency verdict", { ...reviewed, automatedReview: { mode: "LANGUAGE_MODEL", evidenceSufficiency: "NONE" } }],
		["a practice it does not review claiming sufficient evidence", { ...reviewed, automatedReview: { mode: "NONE", evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET" }, knownLimitations: [] }],
		["limitations on a practice it does not review", { ...reviewed, automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" } }],
		// The reason a person is needed is the one field an operator asked about; folding it back into
		// the limitation list is how it stopped being answerable the first time.
		["insufficient evidence with no reason a person is needed", { ...reviewed, automatedReview: { ...reviewed.automatedReview, evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT" } }],
		["a reason a person is needed on a practice it does review", { ...reviewed, insufficiencyReason: reason }],
		["a retired evidence profile", { ...reviewed, evidenceProfile: "pull-request-review" }],
		// The sources a review reads moved onto the bindings, because they depend on what occasioned it.
		["evidence needs on the policy", { ...reviewed, needs: [{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" }] }],
		["a limitation with no code", { ...reviewed, knownLimitations: [{ description: "…" }] }],
	]) {
		expectRejection(id, invalid, `${version}/practice-automated-review-policy.schema.json accepted ${label}`);
	}
};

const catalogDigests = new Map();

const validateContractVersion = async (version) => {
	const versionDir = path.join(contractsRoot, version);
	const catalogBytes = await readFile(path.join(versionDir, "catalog.json"));
	const catalog = JSON.parse(catalogBytes);
	const catalogDigest = createHash("sha256").update(catalogBytes).digest("hex");
	// Both runtime records name the catalog they were interpreted under by digest, so a catalog edit
	// that leaves them behind must fail here rather than produce records nobody can interpret.
	for (const schemaFile of [
		"artifact-source-manifest.schema.json",
		"automated-review-readiness-report.schema.json",
	]) {
		const schema = await readJson(path.join(versionDir, schemaFile));
		if (schema.properties.catalogDigest.const !== catalogDigest) {
			throw new Error(
				`${version}/${schemaFile} pins ${schema.properties.catalogDigest.const} but the catalog hashes to ${catalogDigest}`,
			);
		}
	}
	catalogDigests.set(version, catalogDigest);

	validate(schemaId(version, "artifact-source-catalog.schema.json"), catalog, `${version}/catalog.json`);
	rejectDuplicateProperty(catalog.sources, "kind", `${version} source kind`);
	validate(
		schemaId(version, "source-use-decisions.schema.json"),
		await readJson(path.join(versionDir, "source-use-decisions.json")),
		`${version}/source-use-decisions.json`,
	);

	await validateSchemasTrackTheCatalog(version, catalog);
	validatePolicySchema(version);
	return catalog;
};

for (const version of contractVersions) await validateContractVersion(version);

const practiceCatalogPath = path.join(root, "server/src/main/resources/practices/default-catalog.json");
const practiceCatalog = await readJson(practiceCatalogPath);
validate(defaultCatalogSchema.$id, practiceCatalog, "practices/default-catalog.json");

const precomputeResourcePrefix = "practices/precompute/";
const precomputeScripts = new Set(
	(await readdir(path.join(root, "server/src/main/resources/practices/precompute")))
		.filter((file) => file.endsWith(".ts"))
		.map((file) => precomputeResourcePrefix + file),
);
const referencedPrecomputeScripts = new Set();
for (const area of practiceCatalog.areas) {
	for (const practice of area.practices) {
		if (!practice.precomputeScript) continue;
		// The loader checks the script exists. Nothing checks it belongs to the practice that names it,
		// and a script named after a slug is the only thing that keeps the pair findable from either side.
		const expected = `${precomputeResourcePrefix}${practice.slug}.ts`;
		if (practice.precomputeScript !== expected) {
			throw new Error(
				`default-catalog.json practice '${practice.slug}' must name its precompute script '${expected}'`,
			);
		}
		if (!precomputeScripts.has(expected)) {
			throw new Error(`default-catalog.json practice '${practice.slug}' names a missing precompute script`);
		}
		referencedPrecomputeScripts.add(expected);
	}
}
for (const script of precomputeScripts) {
	if (!referencedPrecomputeScripts.has(script)) {
		throw new Error(`precompute script '${script}' is not named by any bundled practice`);
	}
}

console.log(
	`Artifact-source contracts: ${contractVersions.length} version(s) validated (${contractVersions.join(", ")}); ${practiceCatalog.areas.flatMap((area) => area.practices).length} bundled practices satisfy their schema.`,
);
