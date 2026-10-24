import assert from "node:assert/strict";
import { test } from "node:test";

import {
	type AttemptJob,
	failedTestCases,
	flakyJobs,
	renderFlakes,
	reportArtifacts,
} from "./report-flakes.ts";

const job = (name: string, conclusion: string | null, failedSteps: string[] = []): AttemptJob => ({
	name,
	conclusion,
	url: `https://github.com/example/project/actions/runs/1/job/${name}`,
	failedSteps,
});

void test("a job that failed and then passed on a later attempt of the run is flaky", () => {
	const attempts = new Map([
		[1, [job("Integration", "failure", ["Run integration tests"]), job("Unit", "success")]],
		[2, [job("Integration", "success")]],
	]);
	assert.deepEqual(flakyJobs(attempts), [
		{ attempt: 1, job: job("Integration", "failure", ["Run integration tests"]) },
	]);
});

void test("a failure that never passed again, and the aggregate gate, are not flakes", () => {
	const attempts = new Map([
		[1, [job("Integration", "failure"), job("CI Status Gate", "failure")]],
		[2, [job("Integration", "failure"), job("CI Status Gate", "success")]],
	]);
	assert.deepEqual(flakyJobs(attempts), []);
});

void test("a failure passes only when a later attempt passes, not an earlier one", () => {
	const attempts = new Map([
		[2, [job("Stories", "failure")]],
		[1, [job("Stories", "success")]],
	]);
	assert.deepEqual(flakyJobs(attempts), []);
});

void test("failed and errored JUnit cases are named by class and test", () => {
	const xml = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="de.example.GatewayIntegrationTest" tests="3">
  <testcase name="passes" classname="de.example.GatewayIntegrationTest" time="0.1"/>
  <testcase name="reachesTheEndpoint()" classname="de.example.GatewayIntegrationTest" time="1.2">
    <failure message="expected 200">stack</failure>
  </testcase>
  <testcase name="startsTheSandbox()" classname="de.example.GatewayIntegrationTest" time="0.4">
    <error message="port in use">stack</error>
  </testcase>
</testsuite>`;
	assert.deepEqual(failedTestCases(xml), [
		"de.example.GatewayIntegrationTest > reachesTheEndpoint()",
		"de.example.GatewayIntegrationTest > startsTheSandbox()",
	]);
});

void test("Vitest suites without class names are named by their suite", () => {
	const xml = `<testsuites><testsuite name="src/routes/-mentor-route.test.tsx">
  <testcase name="stops a live answer"><failure message="timed out"/></testcase>
</testsuite></testsuites>`;
	assert.deepEqual(failedTestCases(xml), [
		"src/routes/-mentor-route.test.tsx > stops a live answer",
	]);
});

void test("only unexpired report artifacts of the failed attempt are read", () => {
	const artifacts = [
		{ name: "server-integration-providers-reports-1", expired: false },
		{ name: "server-integration-providers-reports-2", expired: false },
		{ name: "webapp-unit-reports-1", expired: true },
		{ name: "server-build-1", expired: false },
	];
	assert.deepEqual(reportArtifacts(artifacts, 1), ["server-integration-providers-reports-1"]);
});

void test("the report ranks tests and jobs by how often they flaked", () => {
	const rendered = renderFlakes(
		[
			{
				runId: 11,
				sha: "a",
				event: "pull_request",
				createdAt: "2026-10-20T10:00:00Z",
				jobs: [{ attempt: 1, job: job("Integration", "failure", ["Run integration tests"]) }],
				failedTests: ["A > b", "A > b"],
			},
			{
				runId: 12,
				sha: "b",
				event: "merge_group",
				createdAt: "2026-10-22T10:00:00Z",
				jobs: [{ attempt: 1, job: job("Integration", "failure", ["Run integration tests"]) }],
				failedTests: ["A > b"],
			},
			{
				runId: 13,
				sha: "c",
				event: "pull_request",
				createdAt: "2026-10-23T10:00:00Z",
				jobs: [{ attempt: 1, job: job("Windows | Gates", "failure", ["Quality gates"]) }],
				failedTests: null,
			},
		],
		14,
	);
	assert.match(rendered, /3 runs failed and then passed on a rerun of the same commit/u);
	assert.match(rendered, /\| 2 \| 2026-10-22 \| A > b \| 11, 12 \|/u);
	assert.match(rendered, /\| 2 \| 2026-10-22 \| Integration \| Run integration tests \|/u);
	assert.match(rendered, /\| 1 \| 2026-10-23 \| Windows \\\| Gates \| Quality gates \|/u);
});
