import { readFileSync, writeFileSync } from "node:fs";

type Finding = {
	FixedVersion?: string;
	InstalledVersion?: string;
	PkgName?: string;
	Severity?: string;
	VulnerabilityID?: string;
};

type Exception = {
	expires: string;
	image: string;
	installedVersion: string;
	justification: string;
	owner: string;
	package: string;
	vulnerability: string;
};
type Policy = { baseline: string[]; exceptions: Exception[]; schemaVersion: number };

const record = (value: unknown): value is Record<string, unknown> =>
	typeof value === "object" && value !== null && !Array.isArray(value);

const isException = (value: unknown): value is Exception =>
	record(value) &&
	[
		"expires",
		"image",
		"installedVersion",
		"justification",
		"owner",
		"package",
		"vulnerability",
	].every((field) => typeof value[field] === "string");

const isPolicy = (value: unknown): value is Policy =>
	record(value) &&
	value.schemaVersion === 1 &&
	Array.isArray(value.baseline) &&
	value.baseline.every((entry) => typeof entry === "string") &&
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
) {
	if (!record(reportValue) || !Array.isArray(reportValue.Results))
		throw new Error("malformed Trivy report");
	if (!isPolicy(policyValue)) {
		throw new Error("malformed vulnerability policy");
	}
	const policy = policyValue;
	const baseline = new Set(policy.baseline);
	const errors: string[] = [];
	if (baseline.size !== policy.baseline.length) errors.push("baseline contains duplicate entries");
	for (const entry of baseline) {
		if (!/^[^|]+\|[^|]+\|[^|]+\|[^|]+$/.test(entry))
			errors.push(`malformed baseline entry: ${entry}`);
	}
	for (const exception of policy.exceptions) {
		if (
			!exception.owner ||
			!exception.justification ||
			!exception.expires ||
			!exception.image ||
			!exception.installedVersion ||
			!exception.package ||
			!exception.vulnerability
		) {
			errors.push(
				"exception is missing an owner, justification, expiry, image, package, installed version, or vulnerability",
			);
			continue;
		}
		const expiry = new Date(exception.expires);
		if (
			!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(exception.expires) ||
			Number.isNaN(expiry.valueOf()) ||
			expiry <= now
		)
			errors.push(`exception expired: ${exception.vulnerability} (${exception.expires})`);
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
	const actionable = findings.filter(
		(finding) =>
			(finding.Severity === "HIGH" || finding.Severity === "CRITICAL") &&
			typeof finding.FixedVersion === "string" &&
			finding.FixedVersion.trim().length > 0,
	);
	const rejected = actionable.filter((finding) => {
		const id = fingerprint(image, finding);
		if (baseline.has(id)) return false;
		return !policy.exceptions.some(
			(exception) =>
				exception.image === image &&
				exception.package === finding.PkgName &&
				exception.installedVersion === finding.InstalledVersion &&
				exception.vulnerability === finding.VulnerabilityID &&
				new Date(exception.expires) > now,
		);
	});
	return {
		actionable: actionable.map((finding) => fingerprint(image, finding)),
		errors,
		rejected: rejected.map((finding) => fingerprint(image, finding)),
	};
}

if (import.meta.main) {
	const [image, reportPath, policyPath, outputPath] = process.argv.slice(2);
	if (!image || !reportPath || !policyPath || !outputPath)
		throw new Error(
			"usage: check-release-vulnerabilities <image> <trivy.json> <policy.json> <result.json>",
		);
	const result = evaluate(image, parseJson(reportPath), parseJson(policyPath));
	writeFileSync(
		outputPath,
		`${JSON.stringify({ image, status: result.errors.length === 0 && result.rejected.length === 0 ? "pass" : "fail", ...result }, null, 2)}\n`,
	);
	if (result.errors.length > 0 || result.rejected.length > 0) {
		for (const message of [
			...result.errors,
			...result.rejected.map((finding) => `new actionable finding: ${finding}`),
		])
			process.stderr.write(`::error::${message}\n`);
		process.exitCode = 1;
	}
}
