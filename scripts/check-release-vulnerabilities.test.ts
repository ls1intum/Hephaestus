import assert from "node:assert/strict";
import { test } from "node:test";
import { evaluate } from "./check-release-vulnerabilities.ts";

const finding = {
	FixedVersion: "2",
	InstalledVersion: "1",
	PkgName: "lib",
	Severity: "HIGH",
	VulnerabilityID: "CVE-1",
};
const report = { Results: [{ Vulnerabilities: [finding] }] };
const policy = { exceptions: [], schemaVersion: 2 };
const digest = `sha256:${"a".repeat(64)}`;
const subject = { digest, platform: "linux/amd64", reference: `registry.example/server@${digest}` };
const now = new Date("2026-09-01T00:00:00Z");

await test("rejects a high vulnerability without a disposition", () => {
	assert.deepEqual(evaluate("server", report, policy).rejected, ["server|CVE-1|lib|1"]);
});

await test("requires a disposition even when no fixed version exists", () => {
	assert.deepEqual(
		evaluate(
			"server",
			{ Results: [{ Vulnerabilities: [{ ...finding, FixedVersion: "" }] }] },
			policy,
		).rejected,
		["server|CVE-1|lib|1"],
	);
});

await test("accepts an owned, justified, unexpired exception", () => {
	const exceptions = [
		{
			digest,
			evidence: "https://github.com/example/project/issues/1",
			expires: "2026-10-01T00:00:00Z",
			image: "server",
			installedVersion: "1",
			justification: "not reachable in the deployed configuration",
			owner: "@security",
			package: "lib",
			platform: "linux/amd64",
			status: "not_affected",
			vulnerability: "CVE-1",
		},
	];
	assert.deepEqual(
		evaluate(
			"server",
			{ ...report, ArtifactName: subject.reference },
			{ ...policy, exceptions },
			now,
			subject,
		).rejected,
		[],
	);
	assert.deepEqual(
		evaluate(
			"server",
			{ ...report, ArtifactName: subject.reference },
			{ ...policy, exceptions: [{ ...exceptions[0], installedVersion: "0" }] },
			now,
			subject,
		).rejected,
		["server|CVE-1|lib|1"],
	);
	assert.deepEqual(
		evaluate(
			"server",
			{ ...report, ArtifactName: subject.reference },
			{ ...policy, exceptions: [{ ...exceptions[0], digest: `sha256:${"b".repeat(64)}` }] },
			now,
			subject,
		).rejected,
		["server|CVE-1|lib|1"],
	);
});

await test("rejects expired and malformed exceptions", () => {
	const exceptions = [
		{
			digest,
			evidence: "https://github.com/example/project/issues/1",
			expires: "2020-01-01T00:00:00Z",
			image: "server",
			installedVersion: "1",
			justification: "reviewed",
			owner: "@security",
			package: "lib",
			platform: "linux/amd64",
			status: "affected",
			vulnerability: "CVE-1",
		},
	];
	assert.match(
		evaluate("server", report, { ...policy, exceptions }, new Date("2021-01-01")).errors[0] ?? "",
		/expired/,
	);
	assert.throws(() => evaluate("server", {}, policy), /malformed Trivy report/);
	assert.throws(
		() => evaluate("server", { Results: [{ Vulnerabilities: [{ Severity: "HIGH" }] }] }, policy),
		/missing InstalledVersion/,
	);
	assert.match(
		evaluate("server", report, {
			...policy,
			exceptions: [exceptions[0], exceptions[0]],
		}).errors.join("\n"),
		/duplicate exception/,
	);
	assert.match(
		evaluate(
			"server",
			report,
			{
				...policy,
				exceptions: [{ ...exceptions[0], expires: "2027-01-01T00:00:00Z" }],
			},
			now,
		).errors.join("\n"),
		/90-day limit/,
	);
	assert.match(
		evaluate(
			"server",
			{ ...report, ArtifactName: "registry.example/server@sha256:wrong" },
			policy,
			new Date(),
			subject,
		).errors.join("\n"),
		/Trivy report is for/,
	);
});
