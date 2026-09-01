import { appendFileSync, readFileSync, writeFileSync } from "node:fs";

type Finding = {
	FixedVersion?: string;
	InstalledVersion?: string;
	PkgName?: string;
	Severity?: string;
	VulnerabilityID?: string;
};

/**
 * The five `not_affected` justifications from CISA's *Minimum Requirements for Vulnerability
 * Exploitability eXchange (VEX)*. They are taken as a vocabulary only: a claim that a finding does
 * not apply has to name which of the five ways it does not apply, so prose like "not reachable"
 * cannot stand in for an analysis nobody did. VEX itself is not adopted — see
 * `docs/contributor/vulnerability-remediation.mdx`.
 */
const JUSTIFICATION_CATEGORIES = [
	"component_not_present",
	"vulnerable_code_not_present",
	"vulnerable_code_not_in_execute_path",
	"vulnerable_code_cannot_be_controlled_by_adversary",
	"inline_mitigations_already_exist",
] as const;

export type JustificationCategory = (typeof JUSTIFICATION_CATEGORIES)[number];

const isJustificationCategory = (value: unknown): value is JustificationCategory =>
	JUSTIFICATION_CATEGORIES.some((category) => category === value);

// `digest` is recorded so an exception names the artefact it was reviewed against, but it is
// deliberately not part of the match key: the release digest does not exist until `tag-images`
// runs, so a digest-keyed exception could only ever be authored after the gate had already
// failed a release, and would die on the next rebuild. `installedVersion` already gives the
// "this exception expires when the package moves" property that digest matching was there for.
type Exception = {
	digest: string;
	evidence: string;
	expires: string;
	image: string;
	installedVersion: string;
	justification: string;
	/** Required when `status` is `not_affected`, and forbidden otherwise. */
	justificationCategory?: JustificationCategory;
	owner: string;
	package: string;
	platform: string;
	status: "affected" | "not_affected";
	vulnerability: string;
};
type Policy = { exceptions: Exception[]; schemaVersion: 2 };

const record = (value: unknown): value is Record<string, unknown> =>
	typeof value === "object" && value !== null && !Array.isArray(value);

const isException = (value: unknown): value is Exception =>
	record(value) &&
	[
		"digest",
		"evidence",
		"expires",
		"image",
		"installedVersion",
		"justification",
		"owner",
		"package",
		"platform",
		"status",
		"vulnerability",
	].every((field) => typeof value[field] === "string") &&
	(value.status === "affected" || value.status === "not_affected") &&
	(value.justificationCategory === undefined ||
		isJustificationCategory(value.justificationCategory));

const isPolicy = (value: unknown): value is Policy =>
	record(value) &&
	value.schemaVersion === 2 &&
	Array.isArray(value.exceptions) &&
	value.exceptions.every(isException);

function parseJson(path: string): unknown {
	return JSON.parse(readFileSync(path, "utf8")) as unknown;
}

function fingerprint(image: string, finding: Finding): string {
	return `${image}|${finding.VulnerabilityID}|${finding.PkgName}|${finding.InstalledVersion}`;
}

export function evaluate(
	image: string,
	reportValue: unknown,
	policyValue: unknown,
	now = new Date(),
	subject?: { digest: string; platform: string; reference: string },
) {
	if (!record(reportValue) || !Array.isArray(reportValue.Results))
		throw new Error("malformed Trivy report");
	if (!isPolicy(policyValue)) {
		throw new Error("malformed vulnerability policy");
	}
	const policy = policyValue;
	const errors: string[] = [];
	if (subject && reportValue.ArtifactName !== subject.reference)
		errors.push(
			`Trivy report is for ${String(reportValue.ArtifactName)}, not ${subject.reference}`,
		);
	const exceptionKeys = new Set<string>();
	for (const exception of policy.exceptions) {
		if (
			!exception.owner.trim() ||
			!exception.justification.trim() ||
			!exception.expires ||
			!exception.image ||
			!exception.digest ||
			!exception.evidence ||
			!exception.installedVersion ||
			!exception.package ||
			!exception.platform ||
			!exception.vulnerability
		) {
			errors.push(
				"exception is missing subject, status, evidence, owner, justification, expiry, package, installed version, or vulnerability",
			);
			continue;
		}
		const key = `${exception.image}|${exception.platform}|${exception.vulnerability}|${exception.package}|${exception.installedVersion}`;
		if (exceptionKeys.has(key)) errors.push(`duplicate exception: ${key}`);
		exceptionKeys.add(key);
		if (!/^sha256:[a-f0-9]{64}$/.test(exception.digest))
			errors.push(`malformed exception digest: ${exception.digest}`);
		if (!/^linux\/(?:amd64|arm64)$/.test(exception.platform))
			errors.push(`unsupported exception platform: ${exception.platform}`);
		// An unrecognised category is already a malformed policy; what is left to check is that the
		// category and the status agree. `affected` defers a risk we accept, and saying why the
		// finding does not apply while conceding that it does is a contradiction, not a detail.
		if (exception.status === "not_affected" && exception.justificationCategory === undefined)
			errors.push(
				`not_affected exception must name a justification category: ${exception.vulnerability}`,
			);
		if (exception.status === "affected" && exception.justificationCategory !== undefined)
			errors.push(
				`affected exception must not name a justification category: ${exception.vulnerability}`,
			);
		try {
			if (new URL(exception.evidence).protocol !== "https:")
				errors.push(`exception evidence must be an HTTPS URL: ${exception.evidence}`);
		} catch {
			errors.push(`exception evidence must be an HTTPS URL: ${exception.evidence}`);
		}
		const expiry = new Date(exception.expires);
		if (
			!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(exception.expires) ||
			Number.isNaN(expiry.valueOf()) ||
			expiry <= now
		)
			errors.push(`exception expired: ${exception.vulnerability} (${exception.expires})`);
		else if (expiry.valueOf() - now.valueOf() > 90 * 24 * 60 * 60 * 1000)
			errors.push(
				`exception exceeds 90-day limit: ${exception.vulnerability} (${exception.expires})`,
			);
	}
	const findings: Finding[] = [];
	for (const result of reportValue.Results) {
		if (
			!record(result) ||
			(result.Vulnerabilities != null && !Array.isArray(result.Vulnerabilities))
		)
			throw new Error("malformed Trivy result");
		for (const value of result.Vulnerabilities ?? []) {
			if (!record(value)) throw new Error("malformed Trivy vulnerability");
			for (const field of ["InstalledVersion", "PkgName", "Severity", "VulnerabilityID"])
				if (typeof value[field] !== "string" || value[field].length === 0)
					throw new Error(`malformed Trivy vulnerability: missing ${field}`);
			// Trivy omits FixedVersion entirely when upstream has published no fix, so its
			// absence is data rather than corruption; only a non-string is malformed.
			if (value.FixedVersion !== undefined && typeof value.FixedVersion !== "string")
				throw new Error("malformed Trivy vulnerability: FixedVersion must be a string");
			findings.push({
				FixedVersion: typeof value.FixedVersion === "string" ? value.FixedVersion : undefined,
				InstalledVersion:
					typeof value.InstalledVersion === "string" ? value.InstalledVersion : undefined,
				PkgName: typeof value.PkgName === "string" ? value.PkgName : undefined,
				Severity: typeof value.Severity === "string" ? value.Severity : undefined,
				VulnerabilityID:
					typeof value.VulnerabilityID === "string" ? value.VulnerabilityID : undefined,
			});
		}
	}
	const highCritical = findings.filter(
		(finding) => finding.Severity === "HIGH" || finding.Severity === "CRITICAL",
	);
	const subjectPlatform = subject?.platform;
	// Only a finding upstream has published a fix for can block: the upstream images in
	// security/release-images.json cannot be patched at all, so failing on an unfixable
	// finding removes no risk and leaves nobody an action. The filter lives here rather than
	// on Trivy's command line (`--ignore-unfixed`) so unfixable findings stay counted in
	// `highCritical` and visible in the signed evidence bundle.
	const rejected = highCritical.filter((finding) => {
		if (!finding.FixedVersion?.trim()) return false;
		return !policy.exceptions.some(
			(exception) =>
				exception.image === image &&
				exception.platform === subjectPlatform &&
				exception.package === finding.PkgName &&
				exception.installedVersion === finding.InstalledVersion &&
				exception.vulnerability === finding.VulnerabilityID &&
				new Date(exception.expires) > now,
		);
	});
	return {
		highCritical: highCritical.map((finding) => fingerprint(image, finding)),
		errors,
		rejected: rejected.map((finding) => fingerprint(image, finding)),
	};
}

export type Evaluation = ReturnType<typeof evaluate>;

/**
 * A run summary naming what was rejected. The gate used to fail with nothing but
 * `<image> does not satisfy vulnerability policy`, so diagnosing it meant rebuilding and
 * rescanning the base image by hand.
 */
export function renderSummary(
	subject: { digest: string; image: string; platform: string },
	result: Evaluation,
): string {
	const status = result.errors.length === 0 && result.rejected.length === 0 ? "pass" : "fail";
	const lines = [
		`### Vulnerability policy — ${subject.image} (${subject.platform}): ${status}`,
		"",
		`Subject \`${subject.digest}\` — ${result.highCritical.length} HIGH/CRITICAL, ${result.rejected.length} rejected.`,
	];
	if (result.rejected.length > 0) {
		lines.push("", "| Vulnerability | Package | Installed |", "| --- | --- | --- |");
		for (const finding of result.rejected) {
			const [, vulnerability = "", packageName = "", installedVersion = ""] = finding.split("|");
			lines.push(`| ${vulnerability} | ${packageName} | ${installedVersion} |`);
		}
	}
	if (result.errors.length > 0) lines.push("", ...result.errors.map((message) => `- ${message}`));
	return `${lines.join("\n")}\n`;
}

if (import.meta.main) {
	const [image, platform, digest, repository, reportPath, policyPath, outputPath] =
		process.argv.slice(2);
	if (!image || !platform || !digest || !repository || !reportPath || !policyPath || !outputPath)
		throw new Error(
			"usage: check-release-vulnerabilities <image> <platform> <digest> <repository> <trivy.json> <policy.json> <result.json>",
		);
	if (!/^linux\/(?:amd64|arm64)$/.test(platform)) throw new Error("unsupported platform");
	if (!/^sha256:[a-f0-9]{64}$/.test(digest)) throw new Error("malformed subject digest");
	const reference = `${repository}@${digest}`;
	const result = evaluate(image, parseJson(reportPath), parseJson(policyPath), new Date(), {
		digest,
		platform,
		reference,
	});
	writeFileSync(
		outputPath,
		`${JSON.stringify({ image, platform, digest, status: result.errors.length === 0 && result.rejected.length === 0 ? "pass" : "fail", ...result }, null, 2)}\n`,
	);
	const summaryPath = process.env.GITHUB_STEP_SUMMARY;
	if (summaryPath) appendFileSync(summaryPath, renderSummary({ digest, image, platform }, result));
	if (result.errors.length > 0 || result.rejected.length > 0) {
		for (const message of [
			...result.errors,
			...result.rejected.map((finding) => `undispositioned finding: ${finding}`),
		])
			process.stderr.write(`::error::${message}\n`);
		process.exitCode = 1;
	}
}
