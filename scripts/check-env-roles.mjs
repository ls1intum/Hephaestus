#!/usr/bin/env node
/**
 * A setting must reach a container that can actually read it.
 *
 * Runtime roles (`hephaestus.runtime.<role>.enabled`, ADR 0005 / ADR 0008) decide which beans exist
 * in a container. A `@ConfigurationProperties` record read only by role-gated beans is, on a
 * container running with that role off, a variable nothing binds: Compose sets it, `docker inspect`
 * shows it, the documented procedure quotes it, and it configures nothing. That is how a disk bound
 * for the webhook streams was delivered to the container that does not run the webhook role, leaving
 * both the bound and the recovery procedure inert with no signal anywhere.
 *
 * Three failures, all of them the same defect seen from a different side:
 *
 *   misdelivered — a service sets a variable whose owning role that same service disables.
 *   undelivered  — the deployment forwards the variable somewhere, but no container that runs the
 *                  owning role receives it. This is the half that stays broken after the misdelivery
 *                  is removed from the wrong service and never added to the right one.
 *   unforwarded  — `application.yml` offers the knob as `${VAR:default}` and no service forwards it
 *                  at all, so setting it in `.env` does nothing. A setting that is not meant to be
 *                  operator-tunable is written without a placeholder and is never in scope here.
 *
 * `ROLE_SCOPES` is the only thing to extend. It is keyed on `application.yml` paths rather than on
 * whole property records because ownership is finer than a record: `hephaestus.webhook.secret` is
 * read on the server role (outbound registration) while `hephaestus.webhook.stream.*` is read on the
 * webhook role, out of the same `WebhookProperties`. A scope naming a path `application.yml` no
 * longer has fails too, so a rename cannot leave a dead entry behind that silently checks nothing.
 *
 * Compose is read here rather than through `docker compose config`, which would be a stricter parse:
 * this gate runs in `pnpm run check` and the pre-push hook, where a Docker daemon is not a given, and
 * `config` interpolates from the ambient environment, so its verdict would depend on whose shell it
 * ran in. What the shipped topology does with nothing set is the question, so the `${VAR:-default}`
 * defaults are what gets evaluated. A compose file that yields no services fails rather than passes,
 * which is the failure mode a hand-written parse otherwise hides.
 */
import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const APPLICATION_YML = "server/src/main/resources/application.yml";
/** The production topology. Both files together are one deployment, split by role. */
const COMPOSE_FILES = ["docker/compose.app.yaml", "docker/compose.core.yaml"];

/**
 * `application.yml` paths whose beans exist on one runtime role only. Anything not listed is assumed
 * readable everywhere, which is the safe default: it can only miss a check, never invent one.
 */
const ROLE_SCOPES = [
	{
		path: "hephaestus.webhook.stream",
		role: "webhook",
		why: "WebhookJetStreamBootstrap and WebhookStreamMonitor are contributed by WebhookProducerBeans, which WebhookConfiguration gates on hephaestus.runtime.webhook.enabled",
	},
	{
		path: "hephaestus.webhook.publish",
		role: "webhook",
		why: "the publish retry policy and JetStreamPublisher are webhook-role beans",
	},
	{
		path: "hephaestus.webhook.shutdown",
		role: "webhook",
		why: "WebhookGracefulShutdown is a webhook-role bean",
	},
	{
		path: "hephaestus.webhook.http",
		role: "webhook",
		why: "WebhookPayloadSizeFilter is registered by WebhookHttpConfiguration, which is @ConditionalOnWebhookRole",
	},
	{
		path: "hephaestus.integration.consumer",
		role: "server",
		why: "IntegrationNatsConsumer and IntegrationConsumerHealthIndicator are gated on hephaestus.runtime.server.enabled",
	},
];

const ROLE_FLAGS = {
	server: "HEPHAESTUS_RUNTIME_SERVER_ENABLED",
	worker: "HEPHAESTUS_RUNTIME_WORKER_ENABLED",
	webhook: "HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED",
};

/** `application-<profile>.yml` turns roles off too, so a service's profiles decide as much as its env. */
const PROFILE_YML = (profile) => `server/src/main/resources/application-${profile}.yml`;
const PROFILES = ["prod", "worker", "webhook", "local", "e2e"];

/**
 * Which roles each shipped profile overlay switches off. The gate is weaker without it — a webhook
 * variable on a container that disables the role through a profile rather than through its own
 * environment passes — so the shipped-topology test has to build it the same way the CLI does.
 */
export async function readProfileRoles(root = REPO_ROOT) {
	const profileRoles = new Map();
	for (const profile of PROFILES) {
		try {
			profileRoles.set(
				profile,
				readDisabledRoles(await readFile(join(root, PROFILE_YML(profile)), "utf8")),
			);
		} catch {
			// A profile with no overlay file turns nothing off.
		}
	}
	return profileRoles;
}

/** `${VAR:default}` — Spring's syntax. Stops at `$` so a nested placeholder is skipped, not misread. */
const SPRING_PLACEHOLDER = /\$\{([A-Z0-9_]+):[^}$]*\}/;

const indentOf = (line) => line.length - line.trimStart().length;
const isStructural = (line) => line.trim().length > 0 && !line.trim().startsWith("#");

/**
 * What a Compose value resolves to when the operator sets nothing — which is the shipped topology's
 * own answer, and the only one that is the same on every machine. `${VAR:-d}` and `${VAR-d}` fall
 * back to `d`; a bare `${VAR}` resolves to the empty string, exactly as Compose leaves it.
 */
export function composeDefault(raw) {
	const value = raw.trim().replace(/^["']|["']$/g, "");
	const placeholder = /^\$\{([A-Z0-9_]+)(:?-)?([^}]*)\}$/.exec(value);
	if (!placeholder) return value;
	return placeholder[2] ? placeholder[3] : "";
}

/** Every `key: value` in a YAML mapping, with the path of keys above it. Comments and lists are skipped. */
function* mappingEntries(text) {
	const stack = [];
	for (const line of text.split("\n")) {
		if (!isStructural(line)) continue;
		const indent = indentOf(line);
		const match = /^(<<|[A-Za-z0-9_.-]+):\s*(.*)$/.exec(line.trim());
		if (!match) continue;
		while (stack.length > 0 && stack[stack.length - 1].indent >= indent) stack.pop();
		const [, key, value] = match;
		yield { path: [...stack.map((entry) => entry.key), key], key, value: value.trim() };
		stack.push({ indent, key });
	}
}

/** Every key path in `application.yml`, and the `${VAR}` placeholders the paths carry. */
export function readApplicationConfig(text) {
	const paths = new Set();
	const placeholders = new Map();
	for (const { path, value } of mappingEntries(text)) {
		paths.add(path.join("."));
		const variable = SPRING_PLACEHOLDER.exec(value)?.[1];
		if (variable) placeholders.set(variable, path.join("."));
	}
	return { paths, placeholders };
}

/**
 * Services in one Compose file, each with the environment keys it delivers and the role flags it
 * resolves to. Merge keys are resolved against the file's own `x-*` anchors, so a shared block counts
 * as delivered by every service that merges it. Indentation is read relatively, so reformatting the
 * file cannot quietly empty the result.
 */
export function readComposeServices(text) {
	const anchors = new Map();
	const anchoredAt = new Map();
	const services = new Map();

	for (const { path, key, value } of mappingEntries(text)) {
		const declared = /^&([A-Za-z0-9_-]+)/.exec(value);
		if (path.length === 1 && declared) {
			anchors.set(declared[1], new Set());
			anchoredAt.set(key, declared[1]);
			continue;
		}
		if (path.length === 2 && anchoredAt.has(path[0])) {
			anchors.get(anchoredAt.get(path[0])).add(key);
			continue;
		}
		if (path[0] !== "services") continue;
		if (path.length === 2) {
			services.set(key, { name: key, env: new Set(), flags: new Map() });
			continue;
		}
		const service = services.get(path[1]);
		if (!service || path.length !== 4 || path[2] !== "environment") continue;
		if (key === "<<") {
			for (const merged of value.matchAll(/\*([A-Za-z0-9_-]+)/g)) {
				for (const inherited of anchors.get(merged[1]) ?? []) service.env.add(inherited);
			}
			continue;
		}
		service.env.add(key);
		service.flags.set(key, composeDefault(value));
	}
	return services;
}

/**
 * A role is on unless something turns it off: the service's own environment, or a profile overlay it
 * activates. The application defaults every role on, so silence means yes.
 */
const runsRole = (service, role, profileRoles) => {
	if (service.flags.get(ROLE_FLAGS[role]) === "false") return false;
	const profiles = (service.flags.get("SPRING_PROFILES_ACTIVE") ?? "")
		.split(",")
		.map((p) => p.trim());
	return !profiles.some((profile) => profileRoles.get(profile)?.has(role));
};

/** Roles an `application-<profile>.yml` overlay switches off. */
export function readDisabledRoles(text) {
	const disabled = new Set();
	let runtimeIndent = null;
	let role = null;
	for (const line of text.split("\n")) {
		if (!isStructural(line)) continue;
		const indent = indentOf(line);
		const trimmed = line.trim();
		if (runtimeIndent !== null && indent <= runtimeIndent) {
			runtimeIndent = null;
			role = null;
		}
		if (trimmed === "runtime:") {
			runtimeIndent = indent;
			continue;
		}
		if (runtimeIndent === null) continue;
		const key = /^([a-z-]+):$/.exec(trimmed)?.[1];
		if (key) {
			role = key;
			continue;
		}
		if (role && trimmed === "enabled: false") disabled.add(role);
	}
	return disabled;
}

export function analyse(applicationText, compose, profileRoles = new Map()) {
	const { paths, placeholders } = readApplicationConfig(applicationText);

	// Longest path first so a nested scope wins over its parent.
	const scopeOrder = [...ROLE_SCOPES].sort((a, b) => b.path.length - a.path.length);
	const ownership = new Map();
	for (const [variable, path] of placeholders) {
		const scope = scopeOrder.find((s) => path === s.path || path.startsWith(`${s.path}.`));
		if (scope) ownership.set(variable, scope);
	}

	const failures = [];
	for (const scope of ROLE_SCOPES) {
		if (!paths.has(scope.path)) {
			failures.push(
				`ROLE_SCOPES declares "${scope.path}" (${scope.role} role), which ${APPLICATION_YML} does not have.\n` +
					"  Point it at wherever the setting moved, or drop the entry — as written it checks nothing.",
			);
		}
	}

	const delivered = new Map();
	for (const [label, text] of compose) {
		const services = readComposeServices(text);
		if (services.size === 0) {
			failures.push(
				`${label} parsed to zero services, so every check below ran against nothing.\n` +
					"  Either the file is not a Compose file or its shape has moved past what this script reads.",
			);
		}
		for (const [name, service] of services) {
			const id = `${label}:${name}`;
			for (const variable of service.env) {
				const scope = ownership.get(variable);
				if (!scope) continue;
				if (!delivered.has(variable)) delivered.set(variable, []);
				delivered.get(variable).push({ id, service, scope });
				if (!runsRole(service, scope.role, profileRoles)) {
					failures.push(
						`${id} sets ${variable}, and disables the ${scope.role} role that reads it.\n` +
							`  ${variable} binds ${scope.path} — ${scope.why}.\n` +
							"  On this container those beans do not exist, so the variable configures nothing.\n" +
							`  Move it to a service that runs the ${scope.role} role.`,
					);
				}
			}
		}
	}

	for (const [variable, deliveries] of delivered) {
		const scope = deliveries[0].scope;
		if (deliveries.some(({ service }) => runsRole(service, scope.role, profileRoles))) continue;
		failures.push(
			`${variable} is forwarded by ${deliveries.map((d) => d.id).join(", ")}, but no service running the ${scope.role} role receives it.\n` +
				`  ${variable} binds ${scope.path} — ${scope.why}.\n` +
				"  Nothing in the deployment can read it, so it and anything documented around it are inert.",
		);
	}

	for (const [variable, scope] of ownership) {
		if (delivered.has(variable)) continue;
		failures.push(
			`${variable} is offered by ${APPLICATION_YML} but no service in the deployment forwards it.\n` +
				`  ${variable} binds ${scope.path} — ${scope.why}.\n` +
				"  The placeholder is what makes it an operator knob, so setting it in .env reaches nothing.\n" +
				`  Forward it from a service that runs the ${scope.role} role, or drop the placeholder.`,
		);
	}

	return { failures, delivered };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
	const compose = [];
	for (const file of COMPOSE_FILES) {
		compose.push([file, await readFile(join(REPO_ROOT, file), "utf8")]);
	}
	const profileRoles = await readProfileRoles();
	const { failures, delivered } = analyse(
		await readFile(join(REPO_ROOT, APPLICATION_YML), "utf8"),
		compose,
		profileRoles,
	);
	if (failures.length > 0) {
		for (const failure of failures) console.error(`${failure}\n`);
		process.exit(1);
	}
	console.log(
		`check-env-roles: ${delivered.size} role-scoped variable(s) reach a container that runs their role.`,
	);
}
