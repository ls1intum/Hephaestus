import assert from "node:assert/strict";
import { describe, test } from "node:test";

import {
	cleanupPreview,
	listPreviews,
	parseCommand,
	parseConfig,
	type DockerClient,
} from "./preview-host-cleanup.ts";

const config = parseConfig(`
COOLIFY_APP_UUID=app
COOLIFY_APPLICATION_ID=3
COOLIFY_CLEANUP_GRACE_ATTEMPTS=0
`);

interface DockerState {
	containers: Map<string, Record<string, string>>;
	networks: Set<string>;
	volumes: Set<string>;
}

function fakeDocker(state: DockerState): DockerClient {
	return {
		run: (arguments_) => {
			const [resource, action, ...rest] = arguments_;
			if (resource === "ps") {
				const requestedPr = rest
					.find((value) => value.startsWith("label=coolify.pullRequestId="))
					?.split("=")
					.at(-1);
				const requestedApplication = rest
					.find((value) => value.startsWith("label=coolify.applicationId="))
					?.split("=")
					.at(-1);
				const identifiers = [...state.containers]
					.filter(([, labels]) => labels["coolify.applicationId"] === requestedApplication)
					.filter(([, labels]) => !requestedPr || labels["coolify.pullRequestId"] === requestedPr);
				return arguments_.includes("-a")
					? identifiers.map(([, labels]) => labels["coolify.pullRequestId"]).join("\n")
					: identifiers.map(([identifier]) => identifier).join("\n");
			}
			if (resource === "inspect") {
				return JSON.stringify(state.containers.get(rest.at(-1) ?? ""));
			}
			if (resource === "rm" && action === "-f") {
				for (const identifier of rest) state.containers.delete(identifier);
				return "";
			}
			if (resource === "volume" && action === "ls") return [...state.volumes].join("\n");
			if (resource === "volume" && action === "rm") {
				for (const name of rest) state.volumes.delete(name);
				return "";
			}
			if (resource === "network" && action === "ls") return [...state.networks].join("\n");
			if (resource === "network" && action === "disconnect") return "";
			if (resource === "network" && action === "rm") {
				for (const name of rest) state.networks.delete(name);
				return "";
			}
			throw new Error(`unexpected Docker arguments: ${arguments_.join(" ")}`);
		},
	};
}

void describe("preview cleanup configuration", () => {
	void test("accepts only the three documented keys", () => {
		assert.deepEqual(config, { appUuid: "app", applicationId: "3", graceAttempts: 0 });
		assert.throws(() => parseConfig("COOLIFY_APP_UUID=app\nUNKNOWN=value\n"), /unknown config key/);
		assert.throws(
			() => parseConfig("COOLIFY_APP_UUID=app\nCOOLIFY_APP_UUID=other\nCOOLIFY_APPLICATION_ID=3"),
			/duplicate/,
		);
	});

	void test("rejects shell syntax and non-canonical PR numbers", () => {
		assert.deepEqual(parseCommand("cleanup 7"), { name: "cleanup", prNumber: 7 });
		assert.deepEqual(parseCommand("list"), { name: "list" });
		assert.deepEqual(parseCommand("version"), { name: "version" });
		assert.deepEqual(parseCommand("prune"), { name: "prune" });
		for (const command of [
			"cleanup 7; id",
			"cleanup 07",
			"cleanup -1",
			"cleanup 9007199254740993",
			"list now",
			"version now",
			"prune 7",
		]) {
			assert.throws(() => parseCommand(command), /command not allowed/);
		}
	});
});

void describe("preview host inventory and cleanup", () => {
	void test("derives inventory from containers, volumes, and both network formats", () => {
		const state: DockerState = {
			containers: new Map([
				["container-7", { "coolify.applicationId": "3", "coolify.pullRequestId": "7" }],
			]),
			networks: new Set(["app-8", "app_frontend-pr-9", "other-10"]),
			volumes: new Set(["app_postgres-data-pr-11", "unrelated"]),
		};
		assert.deepEqual(listPreviews(config, fakeDocker(state)), [7, 8, 9, 11]);
	});

	void test("removes only the exact application's exact PR resources and verifies absence", async () => {
		const state: DockerState = {
			containers: new Map([
				["container-7", { "coolify.applicationId": "3", "coolify.pullRequestId": "7" }],
				["container-8", { "coolify.applicationId": "3", "coolify.pullRequestId": "8" }],
				["other-app-7", { "coolify.applicationId": "4", "coolify.pullRequestId": "7" }],
			]),
			networks: new Set(["app-7", "app-8"]),
			volumes: new Set(["app_postgres-data-pr-7", "app_postgres-data-pr-8"]),
		};
		await cleanupPreview(config, fakeDocker(state), 7);

		assert.deepEqual([...state.containers.keys()], ["container-8", "other-app-7"]);
		assert.deepEqual([...state.networks], ["app-8"]);
		assert.deepEqual([...state.volumes], ["app_postgres-data-pr-8"]);
	});

	void test("returns without waiting when Coolify already removed everything", async () => {
		const state: DockerState = {
			containers: new Map(),
			networks: new Set(),
			volumes: new Set(),
		};
		const graceConfig = parseConfig(
			"COOLIFY_APP_UUID=app\nCOOLIFY_APPLICATION_ID=3\nCOOLIFY_CLEANUP_GRACE_ATTEMPTS=12",
		);
		let sleeps = 0;
		await cleanupPreview(graceConfig, fakeDocker(state), 7, () => {
			sleeps += 1;
			return Promise.resolve();
		});

		assert.equal(sleeps, 0);
	});

	void test("waits out the grace period, then forces removal and says so", async () => {
		const state: DockerState = {
			containers: new Map(),
			networks: new Set(["app-7"]),
			volumes: new Set(),
		};
		const graceConfig = parseConfig(`
		COOLIFY_APP_UUID=app
		COOLIFY_APPLICATION_ID=3
		COOLIFY_CLEANUP_GRACE_ATTEMPTS=2
		`);
		let sleeps = 0;
		const logged: string[] = [];
		await cleanupPreview(
			graceConfig,
			fakeDocker(state),
			7,
			() => {
				sleeps += 1;
				return Promise.resolve();
			},
			(message) => logged.push(message),
		);

		assert.equal(sleeps, 2);
		assert.deepEqual([...state.networks], []);
		assert.match(logged[0] ?? "", /persisted through the cleanup grace period/);
	});

	void test("fails verification when Docker reports removal without removing the resource", async () => {
		const state: DockerState = {
			containers: new Map(),
			networks: new Set(["app-7"]),
			volumes: new Set(),
		};
		const docker = fakeDocker(state);
		const original = docker.run;
		docker.run = (arguments_, allowFailure) => {
			if (arguments_[0] === "network" && arguments_[1] === "rm") return "";
			return original(arguments_, allowFailure);
		};

		await assert.rejects(cleanupPreview(config, docker, 7), /resources remain/);
	});
});
