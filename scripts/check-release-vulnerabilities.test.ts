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
const policy = { baseline: [], exceptions: [], schemaVersion: 1 };

await test("rejects a new actionable fixed vulnerability", () => {
	assert.deepEqual(evaluate("server", report, policy).rejected, ["server|CVE-1|lib|1"]);
});

await test("accepts an explicitly baselined installed version", () => {
	assert.deepEqual(
		evaluate("server", report, { ...policy, baseline: ["server|CVE-1|lib|1"] }).rejected,
		[],
	);
});

await test("does not classify an unfixed vulnerability as actionable", () => {
	assert.deepEqual(
		evaluate(
			"server",
			{ Results: [{ Vulnerabilities: [{ ...finding, FixedVersion: "" }] }] },
			policy,
		).rejected,
		[],
	);
});

await test("accepts an owned, justified, unexpired exception", () => {
	const exceptions = [
		{
			expires: "2099-01-01T00:00:00Z",
			image: "server",
			installedVersion: "1",
			justification: "not reachable in the deployed configuration",
			owner: "@security",
			package: "lib",
			vulnerability: "CVE-1",
		},
	];
	assert.deepEqual(evaluate("server", report, { ...policy, exceptions }).rejected, []);
	assert.deepEqual(
		evaluate("server", report, {
			...policy,
			exceptions: [{ ...exceptions[0], installedVersion: "0" }],
		}).rejected,
		["server|CVE-1|lib|1"],
	);
});

await test("rejects expired and malformed exceptions", () => {
	const exceptions = [
		{
			expires: "2020-01-01T00:00:00Z",
			image: "server",
			installedVersion: "1",
			justification: "reviewed",
			owner: "@security",
			package: "lib",
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
});
