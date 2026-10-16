import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { describe, test } from "node:test";

import { parse } from "yaml";

import { isRecord } from "./lib/json.ts";

import {
	findEnvDrift,
	findSeedPolicyGaps,
	findStaleOmissions,
	findViolations,
	REQUIRED_SWITCHES,
} from "./check-preview-stack.ts";

interface Service {
	environment: Record<string, string>;
	security_opt?: string[];
	cap_drop?: string[];
	cap_add?: string[];
	build?: { context: string; dockerfile?: string };
	deploy?: { resources: { limits: { memory: string } } };
	volumes?: { source: string; read_only?: boolean }[];
	privileged?: boolean;
	network_mode?: string;
	ports?: { target?: number; published?: string; host_ip?: string; protocol?: string }[];
}

interface Stack {
	services: {
		appserver: Service;
		postgres: Service;
		webapp?: Service;
		"seed-loader"?: Service;
	};
	networks: Record<string, { internal?: boolean; external?: boolean; name?: string }>;
}

const sandboxed: Stack = {
	services: {
		appserver: {
			ports: [{ target: 8080, host_ip: "127.0.0.1", protocol: "tcp" }],
			environment: {
				...REQUIRED_SWITCHES,
				HEPHAESTUS_TRUSTED_PROXIES: "172.(1[6-9]|2[0-9]|3[01]).[0-9]{1,3}.[0-9]{1,3}",
				WEBHOOK_SECRET: "0123456789012345678901234567890123456789",
				WEBHOOK_ROUTING_SECRET: "9876543210987654321098765432109876543210",
			},
			deploy: { resources: { limits: { memory: "2147483648" } } },
			security_opt: ["no-new-privileges:true"],
			cap_drop: ["ALL"],
		},
		postgres: {
			ports: [{ target: 5432, host_ip: "127.0.0.1", protocol: "tcp" }],
			environment: {},
			volumes: [{ source: "postgres-data" }],
			deploy: { resources: { limits: { memory: "536870912" } } },
			security_opt: ["no-new-privileges:true"],
			cap_drop: ["ALL"],
			cap_add: ["CHOWN", "DAC_OVERRIDE", "FOWNER", "SETGID", "SETUID"],
		},
	},
	networks: {},
};

/** Clones the good stack, then lets a test break exactly one thing. */
function mutated(change: (stack: Stack) => void): Stack {
	const copy = structuredClone(sandboxed);
	change(copy);
	return copy;
}

void describe("the API path and the OAuth prefix", () => {
	/** A stack whose webapp is served the API at `path` and whose appserver overrides the prefix. */
	function addressed(path: string, override?: string): Stack {
		return mutated((stack) => {
			stack.services.webapp = {
				environment: { APPLICATION_SERVER_URL: `https://pr1.example.com${path}` },
				security_opt: ["no-new-privileges:true"],
				cap_drop: ["ALL"],
				deploy: { resources: { limits: { memory: "1" } } },
			};
			if (override !== undefined) {
				stack.services.appserver.environment.HEPHAESTUS_AUTH_API_BASE_PATH = override;
			}
		});
	}

	// The server normalizes the prefix before it builds a URL with it, so these all mean `/api` and
	// none of them is a mismatch.
	for (const override of ["/api", "api", "/api/", "//api", "  /api  "]) {
		void test(`accepts ${JSON.stringify(override)}, which the server reads as /api`, () => {
			assert.deepEqual(findViolations(addressed("/api", override)), []);
		});
	}

	void test("accepts the profile's own prefix when the stack overrides nothing", () => {
		assert.deepEqual(findViolations(addressed("/api")), []);
	});

	void test("rejects a prefix the API is not served under, which breaks only sign-in", () => {
		const violations = findViolations(addressed("/api", ""));
		assert.equal(violations.length, 1);
		assert.match(violations[0] ?? "", /sign-in would leave the API/u);
	});
});

void describe("preview stack sandbox", () => {
	void test("accepts the stack as this repository ships it", () => {
		assert.deepEqual(findViolations(sandboxed), []);
	});

	void test("rejects every documented way out of the sandbox", () => {
		const escapes: [string, (stack: Stack) => void, RegExp][] = [
			[
				"docker socket, which a read-only mount does not make safe",
				(s) => {
					s.services.postgres.volumes = [{ source: "/var/run/docker.sock", read_only: true }];
				},
				/Docker socket/u,
			],
			[
				"build stage",
				(s) => {
					s.services.postgres.build = { context: "/repo" };
				},
				/builds from pull-request source/u,
			],
			[
				"privileged",
				(s) => {
					s.services.postgres.privileged = true;
				},
				/runs privileged/u,
			],
			[
				"host networking",
				(s) => {
					s.services.postgres.network_mode = "host";
				},
				/network_mode/u,
			],
			[
				"published port",
				(s) => {
					s.services.postgres.ports = [
						{ target: 5432, published: "5432", host_ip: "127.0.0.1", protocol: "tcp" },
					];
				},
				/publishes a port/u,
			],
			[
				"unbounded memory",
				(s) => {
					delete s.services.postgres.deploy;
				},
				/no memory limit/u,
			],
			[
				"a project-scoped network every preview would share",
				(s) => {
					s.networks.backend = { internal: true };
				},
				/every preview would share/u,
			],
		];

		for (const [label, escape, expected] of escapes) {
			const violations = findViolations(mutated(escape));
			assert.equal(violations.length, 1, `${label}: ${violations.join("; ")}`);
			assert.match(violations[0] ?? "", expected, label);
		}
	});

	void test("catches hardening dropped from a service", () => {
		for (const [label, escape, expected] of [
			[
				"privileges",
				(stack: Stack) => {
					stack.services.postgres.security_opt = [];
				},
				/does not set no-new-privileges/u,
			],
			[
				"capabilities",
				(stack: Stack) => {
					stack.services.webapp = {
						environment: {},
						deploy: { resources: { limits: { memory: "1" } } },
					};
				},
				/does not drop all capabilities/u,
			],
			[
				"an unrecorded capability",
				(stack: Stack) => {
					stack.services.postgres.cap_add = ["SYS_ADMIN"];
				},
				/not recorded here/u,
			],
		] as [string, (stack: Stack) => void, RegExp][]) {
			const violations = findViolations(mutated(escape));
			assert.ok(
				violations.some((violation) => expected.test(violation)),
				`${label}: ${violations.join("; ")}`,
			);
		}
	});

	void test("accepts an external network, which names one that already exists", () => {
		const violations = findViolations(
			mutated((s) => {
				s.networks["staging-shared"] = { external: true, name: "shared-network" };
			}),
		);

		assert.deepEqual(violations, []);
	});

	void test("catches a flipped integration switch", () => {
		const violations = findViolations(
			mutated((s) => {
				s.services.appserver.environment.AGENT_ENABLED = "true";
			}),
		);

		assert.deepEqual(violations, ["appserver sets AGENT_ENABLED=true, expected false"]);
	});

	void test("catches a renamed integration switch, which Spring would silently default to on", () => {
		const violations = findViolations(
			mutated((s) => {
				delete s.services.appserver.environment.MONITORING_RUN_ON_STARTUP;
			}),
		);

		assert.deepEqual(violations, [
			"appserver sets MONITORING_RUN_ON_STARTUP=«unset», expected false",
		]);
	});

	void test("catches a secret the server refuses to start without", () => {
		const violations = findViolations(
			mutated((s) => {
				s.services.appserver.environment.HEPHAESTUS_TRUSTED_PROXIES = "";
			}),
		);

		assert.match(violations[0] ?? "", /empty HEPHAESTUS_TRUSTED_PROXIES/u);
	});

	void test("refuses output that is not a rendered stack", () => {
		assert.deepEqual(findViolations(null), ["the rendered stack is not an object"]);
		assert.deepEqual(findViolations({}), [
			"the rendered stack declares no services",
			"the rendered stack has no appserver service, so no switch was checked",
		]);
	});
});

function reference(extra: string): string {
	return [
		"services:",
		"  application-server:",
		"    environment:",
		"      DATABASE_URL: postgres",
		extra,
	].join("\n");
}

void describe("preview drift from the reference stack", () => {
	const preview = [
		"services:",
		"  appserver:",
		"    environment:",
		"      DATABASE_URL: postgres",
	].join("\n");

	void test("passes when the preview restates everything the reference sets", () => {
		assert.deepEqual(findEnvDrift(reference(""), preview), []);
	});

	void test("names a reference variable the preview neither sets nor records", () => {
		const drift = findEnvDrift(reference("      BRAND_NEW_SWITCH: true"), preview);

		assert.equal(drift.length, 1);
		assert.match(drift[0] ?? "", /BRAND_NEW_SWITCH/u);
	});

	void test("stays quiet for a variable recorded as deliberately omitted", () => {
		assert.deepEqual(findEnvDrift(reference("      SANDBOX_CPUS: 2"), preview), []);
	});
});

function seedLoader(policy: string, counted: string[]): string {
	return [
		"        psql -h target -v ON_ERROR_STOP=1 <<'SQL'",
		policy,
		"        SQL",
		"",
		'        LIVE=$$(psql -h target -tAX -v ON_ERROR_STOP=1 -c "',
		`          SELECT ${counted.map((table) => `(SELECT count(*) FROM ${table})`).join(" + ")}")`,
		'        if [ "$$LIVE" != "0" ]; then exit 1; fi',
	].join("\n");
}

void describe("preview seed policy", () => {
	void test("passes when the policy clears exactly what the verification counts", () => {
		const stack = seedLoader(
			"        UPDATE llm_connection SET api_key = NULL;\n        DELETE FROM issued_jwt;",
			["llm_connection", "issued_jwt"],
		);

		assert.deepEqual(findSeedPolicyGaps(stack), []);
	});

	void test("names a credential store the policy clears and nothing counts", () => {
		const stack = seedLoader(
			"        UPDATE llm_connection SET api_key = NULL;\n        UPDATE workspace_llm_connection SET api_key = NULL;",
			["llm_connection"],
		);

		assert.deepEqual(findSeedPolicyGaps(stack), [
			"the policy changes workspace_llm_connection but the verification query never counts it",
		]);
	});

	void test("names a store the verification counts and the policy never clears", () => {
		const stack = seedLoader("        UPDATE llm_connection SET api_key = NULL;", [
			"llm_connection",
			"connection",
		]);

		assert.deepEqual(findSeedPolicyGaps(stack), [
			"the verification query counts connection but the policy never changes it",
		]);
	});

	void test("refuses a seed loader that applies or verifies nothing", () => {
		assert.deepEqual(findSeedPolicyGaps("services:\n  seed-loader:\n"), [
			"the seed loader applies no SQL policy",
		]);
		assert.deepEqual(
			findSeedPolicyGaps("        psql <<'SQL'\n        DELETE FROM issued_jwt;\n        SQL\n"),
			["the seed loader marks a clone seeded without verifying its policy"],
		);
	});
});

void test("flags an omission entry whose reference variable no longer exists", () => {
	const stale = findStaleOmissions(
		"services:\n  application-server:\n    environment:\n      KEEP: 1",
	);

	assert.ok(stale.length > 0);
	assert.match(stale[0] ?? "", /no longer sets it/u);
});

void describe("owned preview reviews", () => {
	function enabled(): Stack {
		return mutated((stack) => {
			for (const key of [
				"AGENT_ENABLED",
				"GIT_CHECKOUT_ENABLED",
				"GITLAB_ENABLED",
				"GITLAB_WORKSPACE_CREATION",
				"HEPHAESTUS_AGENT_IMAGE_REQUIRE_DIGEST",
			]) {
				stack.services.appserver.environment[key] = "true";
			}
			stack.services.appserver.environment.HEPHAESTUS_AGENT_IMAGE_REFERENCE = `ghcr.io/hephaestus-build/agent-pi@sha256:${"b".repeat(64)}`;
			stack.services.appserver.environment.HEPHAESTUS_WORKER_HUB_TOKEN_REGISTRATION_TOKEN =
				"private-test-token";
			for (const service of [stack.services.appserver, stack.services.postgres]) {
				for (const port of service.ports ?? []) {
					port.host_ip = "172.19.0.1";
				}
			}
		});
	}

	void test("requires an explicit mode, a pinned agent and a private registration token", () => {
		assert.deepEqual(findViolations(enabled(), true), []);
		assert.ok(findViolations(enabled()).length > 0);
		for (const key of [
			"HEPHAESTUS_AGENT_IMAGE_REFERENCE",
			"HEPHAESTUS_WORKER_HUB_TOKEN_REGISTRATION_TOKEN",
		]) {
			const stack = enabled();
			stack.services.appserver.environment[key] = "";
			assert.equal(findViolations(stack, true).length, 1);
		}
	});

	void test("rejects other interfaces, fixed ports, extra targets and a socket in either mode", () => {
		for (const freshReview of [false, true]) {
			for (const port of [
				{ target: 5432, host_ip: "0.0.0.0", protocol: "tcp" },
				{ target: 5432, host_ip: "192.168.1.1", protocol: "tcp" },
				{ target: 8081, host_ip: freshReview ? "172.19.0.1" : "127.0.0.1", protocol: "tcp" },
				{
					target: 5432,
					host_ip: freshReview ? "172.19.0.1" : "127.0.0.1",
					protocol: "tcp",
					published: "15432",
				},
			]) {
				const stack = freshReview ? enabled() : structuredClone(sandboxed);
				stack.services.postgres.ports = [port];
				assert.ok(
					findViolations(stack, freshReview).some((violation) =>
						violation.includes("publishes a port"),
					),
				);
			}
			const stack = freshReview ? enabled() : structuredClone(sandboxed);
			stack.services.appserver.volumes = [{ source: "/run/user/1024/docker.sock" }];
			assert.ok(
				findViolations(stack, freshReview).some((violation) => violation.includes("Docker socket")),
			);
		}
	});

	void test("runs the shipped seed guard before any already-seeded shortcut", () => {
		const compose: unknown = parse(readFileSync("docker/preview/compose.app.yaml", "utf8"));
		assert.ok(isRecord(compose) && isRecord(compose.services));
		const loader = compose.services["seed-loader"];
		assert.ok(isRecord(loader) && Array.isArray(loader.command));
		const command: unknown = loader.command[2];
		assert.equal(typeof command, "string");
		if (typeof command !== "string") {
			throw new Error("seed-loader has no shell body");
		}
		const start = command.indexOf('case "$$PREVIEW_FRESH_REVIEW_ENABLED"');
		const end = command.indexOf("\nif ! echo");
		assert.ok(start !== -1 && end > start && end < command.indexOf("MARKER="));
		const guard = command.slice(start, end).replaceAll("$$", "$");
		const environment = {
			PREVIEW_FRESH_REVIEW_ENABLED: "true",
			PREVIEW_FRESH_REVIEW_PR: "42",
			SERVICE_FQDN_WEBAPP: "pr42.preview.example.com",
			PREVIEW_WORKER_BIND_HOST: "172.19.0.1",
			PREVIEW_WORKER_REGISTRATION_TOKEN: "private-test-token",
			PREVIEW_AGENT_IMAGE_REFERENCE: `ghcr.io/hephaestus-build/agent-pi@sha256:${"b".repeat(64)}`,
		};
		const run = (overrides: Record<string, string>) =>
			spawnSync("sh", ["-eu", "-c", guard], {
				env: { ...process.env, ...environment, ...overrides },
				encoding: "utf8",
			}).status;
		assert.equal(run({}), 0);
		assert.equal(
			run({ PREVIEW_FRESH_REVIEW_ENABLED: "false", PREVIEW_WORKER_BIND_HOST: "127.0.0.1" }),
			0,
		);
		const refusals: Record<string, string>[] = [
			{ SERVICE_FQDN_WEBAPP: "pr43.preview.example.com" },
			{ SERVICE_FQDN_WEBAPP: "pr420.preview.example.com" },
			{ SERVICE_FQDN_WEBAPP: "preview.example.com" },
			{ PREVIEW_FRESH_REVIEW_PR: "" },
			{ PREVIEW_FRESH_REVIEW_PR: "42.*" },
			{ PREVIEW_FRESH_REVIEW_ENABLED: "yes" },
			{ PREVIEW_WORKER_BIND_HOST: "0.0.0.0" },
			{ PREVIEW_WORKER_REGISTRATION_TOKEN: "" },
			{ PREVIEW_AGENT_IMAGE_REFERENCE: "ghcr.io/hephaestus-build/agent-pi:mutable" },
			{
				PREVIEW_AGENT_IMAGE_REFERENCE: `ghcrXio/hephaestus-build/agent-pi@sha256:${"b".repeat(64)}`,
			},
		];
		for (const overrides of refusals) {
			assert.equal(run(overrides), 1);
		}

		// Run through the real marker shortcut with only the database read replaced.
		const markerEnd = command.indexOf("# Large objects");
		assert.ok(markerEnd > end);
		const markerCheck = command.slice(start, markerEnd).replaceAll("$$", "$");
		const markerRun = (policy: string, freshReview: string) =>
			spawnSync(
				"sh",
				[
					"-eu",
					"-c",
					`psql() { case "$*" in *obj_description*) printf "%s\\n" "$TEST_POLICY" ;; *) printf "%s\\n" "hephaestus_preview_seeded" ;; esac; };\n${markerCheck}`,
				],
				{
					env: {
						...process.env,
						...environment,
						PREVIEW_FRESH_REVIEW_ENABLED: freshReview,
						PREVIEW_WORKER_BIND_HOST: freshReview === "true" ? "172.19.0.1" : "127.0.0.1",
						PREVIEW_SEED_SOURCE_HOST: "source-db",
						SERVICE_NAME_POSTGRES: "preview-db",
						POSTGRES_PASSWORD: "test-password",
						TEST_POLICY: policy,
					},
					encoding: "utf8",
				},
			).status;
		assert.equal(markerRun("", "true"), 1);
		assert.equal(markerRun("owned-review-policy-v1", "true"), 0);
		assert.equal(markerRun("", "false"), 0);
	});
});
