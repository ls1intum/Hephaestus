import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { test } from "node:test";
import {
	analyse,
	type ComposeFile,
	composeDefault,
	readComposeServices,
	readDisabledRoles,
	readProfileRoles,
} from "./check-env-roles.ts";

const REPO_ROOT = resolve(import.meta.dirname, "..");

/**
 * The failure at `index`, asserting there is one. `assert.match` reads a string, and a run that
 * produced fewer failures than the test names is the defect itself, so it fails here saying which.
 */
function failureAt(failures: readonly string[], index: number): string {
	const failure = failures[index];
	assert.ok(failure !== undefined, `no failure at [${index}] among:\n${failures.join("\n")}`);
	return failure;
}

/** Carries every path ROLE_SCOPES names, so a scope that goes stale fails loudly rather than here. */
const APPLICATION = `
hephaestus:
    webhook:
        secret: \${WEBHOOK_SECRET:}
        stream:
            max-bytes: \${HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES:1073741824}
        publish:
            timeout: 9s
        shutdown:
            drain-timeout: 15s
        http:
            max-payload-bytes: 26214400
    integration:
        consumer:
            inactive-threshold: 30d
        github:
            token: \${GH_AUTH_TOKEN:}
`;

const compose = (services: string): ComposeFile[] => [["compose.yaml", `services:\n${services}`]];

/** A container that runs the webhook role, so the scoped variable reaches somewhere that reads it. */
const RECEIVER = `  webhook-server:
    environment:
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "true"
      HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES: \${HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES:-1073741824}
`;

await test("a variable set on a container that disables the role reading it is a failure", () => {
	const { failures } = analyse(
		APPLICATION,
		compose(`  application-server:
    environment:
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "false"
      HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES: \${HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES:-1073741824}
`),
	);

	assert.equal(failures.length, 2, failures.join("\n"));
	assert.match(failureAt(failures, 0), /disables the webhook role that reads it/);
	assert.match(failureAt(failures, 1), /no service running the webhook role receives it/);
});

await test("the role flag is read through its Compose default, not as raw text", () => {
	// The form nearly every other key in these files uses. Comparing the uninterpolated string is
	// how the historical misdelivery passed the gate written to catch it.
	const { failures } = analyse(
		APPLICATION,
		compose(`  application-server:
    environment:
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: \${HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED:-false}
      HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES: \${HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES:-1073741824}
`),
	);

	assert.equal(failures.length, 2, failures.join("\n"));
	assert.match(failureAt(failures, 0), /disables the webhook role that reads it/);
});

await test("removing it from the wrong container without adding it to the right one still fails", () => {
	const { failures } = analyse(
		APPLICATION,
		compose(`  application-server:
    environment:
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "false"
  webhook-server:
    environment:
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "true"
`),
	);

	assert.equal(failures.length, 1, failures.join("\n"));
	assert.match(failureAt(failures, 0), /HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES is offered by/);
	assert.match(failureAt(failures, 0), /no service in the deployment forwards it/);
});

await test("the variable reaching the container that runs its role passes", () => {
	const { failures, delivered } = analyse(
		APPLICATION,
		compose(`  application-server:
    environment:
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "false"
${RECEIVER}      HEPHAESTUS_RUNTIME_SERVER_ENABLED: "false"
`),
	);

	assert.deepEqual(failures, []);
	assert.deepEqual([...delivered.keys()], ["HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES"]);
});

await test("a role is on unless the service turns it off, so an unflagged container counts as a reader", () => {
	const { failures } = analyse(
		APPLICATION,
		compose(`  appserver:
    environment:
      HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES: \${HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES:-1073741824}
`),
	);

	assert.deepEqual(failures, []);
});

await test("variables outside every declared scope are left alone", () => {
	const { failures, delivered } = analyse(
		APPLICATION,
		compose(`  application-server:
    environment:
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "false"
      WEBHOOK_SECRET: \${WEBHOOK_SECRET}
      GH_AUTH_TOKEN: \${GH_AUTH_TOKEN}
${RECEIVER}`),
	);

	assert.deepEqual(failures, []);
	assert.equal(delivered.size, 1);
});

await test("a merged anchor counts as delivered by every service that merges it", () => {
	const services = readComposeServices(`x-shared: &shared
  HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES: 1
services:
  webhook-server:
    environment:
      <<: [*shared]
      HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "true"
`);

	assert.ok(services.get("webhook-server")?.env.has("HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES"));
});

await test("services are found at whatever indentation the file uses", () => {
	const services = readComposeServices(`services:
    webhook-server:
        environment:
            HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED: "true"
`);

	// A scanner keyed on literal indent columns returns nothing here and exits 0, so a reindent
	// silently retires the gate.
	assert.deepEqual([...services.keys()], ["webhook-server"]);
});

await test("a compose file that yields no services is a failure, not a pass", () => {
	const { failures } = analyse(APPLICATION, [["compose.yaml", "name: hephaestus\n"]]);

	assert.ok(
		failures.some((f) => /parsed to zero services/.test(f)),
		failures.join("\n"),
	);
});

await test("compose defaults resolve the way an operator who sets nothing gets them", () => {
	// `\${` throughout: these are Compose interpolation syntax under test, and a plain string spelling
	// them reads as a template literal someone forgot to make one.
	assert.equal(composeDefault(`\${HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED:-false}`), "false");
	assert.equal(composeDefault(`\${HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED-false}`), "false");
	assert.equal(composeDefault('"false"'), "false");
	assert.equal(composeDefault(`\${WEBHOOK_SECRET}`), "");
});

await test("a role a profile overlay switches off does not count as a reader", () => {
	// application-worker.yml turns the webhook role off, so a worker container is not somewhere the
	// webhook stream bounds can be read even though its environment says nothing about the role.
	const profileRoles = new Map([["worker", new Set(["server", "webhook"])]]);

	const { failures } = analyse(
		APPLICATION,
		compose(`  application-worker:
    environment:
      SPRING_PROFILES_ACTIVE: prod,worker
      HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES: \${HEPHAESTUS_WEBHOOK_STREAM_MAX_BYTES:-1073741824}
`),
		profileRoles,
	);

	assert.equal(failures.length, 2, failures.join("\n"));
	assert.match(failureAt(failures, 0), /disables the webhook role that reads it/);
});

await test("every role an overlay switches off is read, not just the first", () => {
	const disabled = readDisabledRoles(`
hephaestus:
    runtime:
        server:
            enabled: false
        webhook:
            enabled: false
        worker:
            enabled: true

    sync:
        nats:
            enabled: true
`);

	assert.deepEqual([...disabled].toSorted(), ["server", "webhook"]);
});

await test("a scope naming a path application.yml does not have is a failure", () => {
	const { failures } = analyse("hephaestus:\n    webhook:\n        secret: x\n", compose(RECEIVER));

	assert.ok(failures.some((f) => /hephaestus\.webhook\.stream.*does not have/s.test(f)));
});

await test("the shipped topology delivers every role-scoped variable to a container that runs its role", async () => {
	const files = ["docker/compose.app.yaml", "docker/compose.core.yaml"];
	const shipped = await Promise.all(
		files.map(
			async (file): Promise<ComposeFile> => [file, await readFile(join(REPO_ROOT, file), "utf8")],
		),
	);
	// With the profile overlays, exactly as the CLI runs it. Omitting them makes this pass on a
	// topology the real gate fails, which is the whole defect class the gate is here for.
	const { failures } = analyse(
		await readFile(join(REPO_ROOT, "server/src/main/resources/application.yml"), "utf8"),
		shipped,
		await readProfileRoles(REPO_ROOT),
	);

	assert.deepEqual(failures, []);
});
