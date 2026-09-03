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
 * The mirror image is a setting no role gates. Every container running the application image reads
 * it, so the value one of them is given is a claim about the whole deployment, and two containers
 * making different claims describe a stack that does not exist:
 *
 *   disagreed    — application containers spell one setting differently. That is how the release
 *                  lock's agent image digest reached the app containers and not the webhook
 *                  receiver, which derived a tag from its own version instead and then refused to
 *                  boot on it, because the guards that read it are deliberately not role-gated.
 *   omitted      — the same defect with the key left out rather than left blank. A container that
 *                  never mentions a setting makes no claim to disagree with, so `DEPLOYMENT_WIDE`
 *                  names the settings whose absence is itself the failure.
 *
 * `ROLE_SCOPES` and `DEPLOYMENT_WIDE` are the two things to extend, and they are opposite claims
 * about the same question — which containers read a setting. Both are keyed on `application.yml`
 * paths rather than on whole property records because ownership is finer than a record:
 * `hephaestus.webhook.secret` is read on the server role (outbound registration) while
 * `hephaestus.webhook.stream.*` is read on the webhook role, out of the same `WebhookProperties`.
 * An entry naming a path `application.yml` no longer has fails too, so a rename cannot leave a dead
 * entry behind that silently checks nothing.
 *
 * Compose is read here rather than through `docker compose config`, which would be a stricter parse:
 * this gate runs in `vp run check` and the pre-push hook, where a Docker daemon is not a given, and
 * `config` interpolates from the ambient environment, so its verdict would depend on whose shell it
 * ran in. What the shipped topology does with nothing set is the question, so the `${VAR:-default}`
 * defaults are what gets evaluated. A compose file that yields no services fails rather than passes,
 * which is the failure mode a hand-written parse otherwise hides.
 */
import { readFile } from "node:fs/promises";
import { join, resolve } from "node:path";

/** Resolved from this file, so the gate answers the same whatever the working directory is. */
const REPO_ROOT = resolve(import.meta.dirname, "..");
const APPLICATION_YML = "server/application/src/main/resources/application.yml";
/** The production topology. Both files together are one deployment, split by role. */
const COMPOSE_FILES = ["docker/compose.app.yaml", "docker/compose.core.yaml"];

/** The runtime roles a container can be given. A scope claiming any other name does not compile. */
type Role = "server" | "worker" | "webhook";

interface RoleScope {
	readonly path: string;
	readonly role: Role;
	/** Named beans, so a reader can check the claim rather than take it. Quoted back in every failure. */
	readonly why: string;
}

/**
 * `application.yml` paths whose beans exist on one runtime role only. Anything not listed is assumed
 * readable everywhere, which is the safe default: it can only miss a check, never invent one.
 */
const ROLE_SCOPES: readonly RoleScope[] = [
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

/** Every role has a flag, so a new role cannot be scoped without saying which variable turns it off. */
const ROLE_FLAGS: Record<Role, string> = {
	server: "HEPHAESTUS_RUNTIME_SERVER_ENABLED",
	worker: "HEPHAESTUS_RUNTIME_WORKER_ENABLED",
	webhook: "HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED",
};

/**
 * The lock variable naming the one image every role boots from. A service running it is an
 * application container: whichever slice of the context its roles leave out, the ungated beans — and
 * therefore every setting not listed in `ROLE_SCOPES` — are there and read on it.
 */
const APPLICATION_IMAGE = "HEPHAESTUS_IMAGE_APPLICATION_SERVER";

/**
 * Keys an application container is meant to spell differently from its siblings, because they are
 * what makes it that slice of the deployment rather than another. Everything else describes the
 * deployment, which is one thing: the roles differ, the stack they run in does not.
 */
const PER_CONTAINER = new Map<string, string>([
	["SPRING_PROFILES_ACTIVE", "the profile list is how a container selects the slice it boots"],
	[ROLE_FLAGS.server, "the role flags are the split itself"],
	[ROLE_FLAGS.worker, "the role flags are the split itself"],
	[ROLE_FLAGS.webhook, "the role flags are the split itself"],
	["SPRING_LIQUIBASE_ENABLED", "one container owns the migration and the rest must not race it"],
	["THC_PATH", "the receiver reports NATS through readiness; the others answer liveness"],
]);

/**
 * A setting every application container must be *given*, not merely agree about. These are the ones
 * whose readers are gated on no role at all: the beans are constructed in every context, so the
 * container that was skipped does not quietly run without the feature, it fails to boot.
 *
 * Declared rather than derived, and the reason is worth stating because it looks like a weakness.
 * Nothing in Compose separates a setting the receiver must have from one it is right not to have:
 * `GH_APP_PRIVATE_KEY` also reaches two of the three containers, and "deliver it to the third too"
 * would be a private key shipped to a container with no use for it. Only the Java says which is
 * which — whether the bean reading it carries a role gate — so the entry has to name that bean and
 * be checkable by a reader against it.
 *
 * What is fail-closed, and needs nothing listed, is the other half: a setting more than one
 * application container writes has to be written the same on all of them. Between the two, both
 * shapes of the defect this exists for are covered — the container that was handed a different
 * value, and the container that was handed none.
 */
interface DeploymentWideSetting {
	readonly variable: string;
	/** The `application.yml` path it binds. A path the file no longer has fails, as in `ROLE_SCOPES`. */
	readonly path: string;
	/** Named beans, so a reader can check the claim rather than take it. Quoted back in every failure. */
	readonly why: string;
}

const DEPLOYMENT_WIDE: readonly DeploymentWideSetting[] = [
	{
		variable: "HEPHAESTUS_AGENT_IMAGE_REFERENCE",
		path: "hephaestus.agent.image.reference",
		why:
			"AgentImageReferenceGuard and AgentImagePinGuard are plain @Components that ADR 0031 keeps " +
			"ungated on purpose, so no pod can boot on an agent image the workers cannot run; a container " +
			"left without the reference derives one from its own version, which is a tag, and refuses to start",
	},
];

/** `application-<profile>.yml` turns roles off too, so a service's profiles decide as much as its env. */
const PROFILE_YML = (profile: string): string =>
	`server/application/src/main/resources/application-${profile}.yml`;
const PROFILES = ["prod", "worker", "webhook", "local", "e2e"];

/**
 * Profile name to the roles that profile's overlay switches off. The names are whatever the overlays
 * spell under `hephaestus.runtime`, not `Role`, so an overlay naming something no longer a role reads
 * back as the dead entry it is instead of being quietly rewritten into a live one.
 */
export type ProfileRoles = ReadonlyMap<string, ReadonlySet<string>>;

/**
 * Which roles each shipped profile overlay switches off. The gate is weaker without it — a webhook
 * variable on a container that disables the role through a profile rather than through its own
 * environment passes — so the shipped-topology test has to build it the same way the CLI does.
 */
export async function readProfileRoles(root = REPO_ROOT): Promise<ProfileRoles> {
	const profileRoles = new Map<string, ReadonlySet<string>>();
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
/** `key: value`, the only line shape read here. `<<` is YAML's merge key and is one of the keys. */
const MAPPING_LINE = /^(<<|[A-Za-z0-9_.-]+):\s*(.*)$/;

const indentOf = (line: string): number => line.length - line.trimStart().length;
const isStructural = (line: string): boolean =>
	line.trim().length > 0 && !line.trim().startsWith("#");

/**
 * What a Compose value resolves to when the operator sets nothing — which is the shipped topology's
 * own answer, and the only one that is the same on every machine. `${VAR:-d}` and `${VAR-d}` fall
 * back to `d`; a bare `${VAR}` resolves to the empty string, exactly as Compose leaves it.
 */
/** The value as the file writes it: trimmed, with the quotes YAML would have dropped anyway. */
const unquote = (raw: string): string => raw.trim().replace(/^["']|["']$/g, "");

export function composeDefault(raw: string): string {
	const value = unquote(raw);
	const placeholder = /^\$\{[A-Z0-9_]+(?<dash>:?-)?(?<fallback>[^}]*)\}$/.exec(value);
	if (!placeholder) return value;
	// The `-` is the only optional part: a placeholder without it offers no fallback at all, and
	// Compose leaves that variable empty. Whatever follows it is the fallback, the empty one included.
	const groups = placeholder.groups;
	return groups?.dash === undefined ? "" : (groups.fallback ?? "");
}

/** One `key: value`. `path` ends in `key`; callers that want a dotted path join it themselves. */
interface MappingEntry {
	readonly path: readonly string[];
	readonly key: string;
	readonly value: string;
}

/** Every `key: value` in a YAML mapping, with the path of keys above it. Comments and lists are skipped. */
function* mappingEntries(text: string): Generator<MappingEntry, void, undefined> {
	const stack: { indent: number; key: string }[] = [];
	for (const line of text.split("\n")) {
		if (!isStructural(line)) continue;
		const indent = indentOf(line);
		// Both groups are mandatory, so either one missing means the line is not a mapping entry.
		const [, key, value] = MAPPING_LINE.exec(line.trim()) ?? [];
		if (key === undefined || value === undefined) continue;
		let parent = stack.at(-1);
		while (parent !== undefined && parent.indent >= indent) {
			stack.pop();
			parent = stack.at(-1);
		}
		yield { path: [...stack.map((entry) => entry.key), key], key, value: value.trim() };
		stack.push({ indent, key });
	}
}

interface ApplicationConfig {
	readonly paths: ReadonlySet<string>;
	/** Variable name to the path it binds. */
	readonly placeholders: ReadonlyMap<string, string>;
}

/** Every key path in `application.yml`, and the `${VAR}` placeholders the paths carry. */
export function readApplicationConfig(text: string): ApplicationConfig {
	const paths = new Set<string>();
	const placeholders = new Map<string, string>();
	for (const { path, value } of mappingEntries(text)) {
		paths.add(path.join("."));
		const variable = SPRING_PLACEHOLDER.exec(value)?.[1];
		if (variable) placeholders.set(variable, path.join("."));
	}
	return { paths, placeholders };
}

/** `env` is every key the service delivers, merges included; `flags` is only what it spells itself. */
export interface ComposeService {
	readonly name: string;
	readonly env: Set<string>;
	readonly flags: Map<string, string>;
	/**
	 * What the file writes for each key it spells itself, before interpolation. `flags` answers what
	 * an operator who sets nothing gets, and two services can agree on that and still be handed
	 * different values — `${VAR:?required}` and a valueless key both resolve to nothing here.
	 */
	readonly raw: Map<string, string>;
	/** The `image:` line, uninterpolated, so a service can be recognised by the image it runs. */
	image: string;
}

/**
 * Services in one Compose file, each with the environment keys it delivers and the role flags it
 * resolves to. Merge keys are resolved against the file's own `x-*` anchors, so a shared block counts
 * as delivered by every service that merges it. Indentation is read relatively, so reformatting the
 * file cannot quietly empty the result.
 */
export function readComposeServices(text: string): Map<string, ComposeService> {
	/** Anchor name to the environment keys its block declares. */
	const anchors = new Map<string, Set<string>>();
	/** Top-level key of an anchored block to that same set, so keys under it land in the anchor. */
	const anchoredAt = new Map<string, Set<string>>();
	const services = new Map<string, ComposeService>();

	for (const { path, key, value } of mappingEntries(text)) {
		const [top, serviceName, section] = path;

		const declared = /^&([A-Za-z0-9_-]+)/.exec(value)?.[1];
		if (path.length === 1 && declared !== undefined) {
			const declaredKeys = new Set<string>();
			anchors.set(declared, declaredKeys);
			anchoredAt.set(key, declaredKeys);
			continue;
		}
		const anchored = path.length === 2 && top !== undefined ? anchoredAt.get(top) : undefined;
		if (anchored) {
			anchored.add(key);
			continue;
		}
		if (top !== "services") continue;
		if (path.length === 2) {
			services.set(key, { name: key, env: new Set(), flags: new Map(), raw: new Map(), image: "" });
			continue;
		}
		if (path.length === 3 && key === "image" && serviceName !== undefined) {
			const named = services.get(serviceName);
			if (named) named.image = unquote(value);
			continue;
		}
		if (path.length !== 4 || section !== "environment" || serviceName === undefined) continue;
		const service = services.get(serviceName);
		if (!service) continue;
		if (key === "<<") {
			for (const [, merged] of value.matchAll(/\*([A-Za-z0-9_-]+)/g)) {
				if (merged === undefined) continue;
				for (const inherited of anchors.get(merged) ?? []) service.env.add(inherited);
			}
			continue;
		}
		service.env.add(key);
		service.flags.set(key, composeDefault(value));
		service.raw.set(key, unquote(value));
	}
	return services;
}

/**
 * A role is on unless something turns it off: the service's own environment, or a profile overlay it
 * activates. The application defaults every role on, so silence means yes.
 */
const runsRole = (service: ComposeService, role: Role, profileRoles: ProfileRoles): boolean => {
	if (service.flags.get(ROLE_FLAGS[role]) === "false") return false;
	const profiles = (service.flags.get("SPRING_PROFILES_ACTIVE") ?? "")
		.split(",")
		.map((p) => p.trim());
	return !profiles.some((profile) => profileRoles.get(profile)?.has(role));
};

/** Roles an `application-<profile>.yml` overlay switches off. */
export function readDisabledRoles(text: string): Set<string> {
	const disabled = new Set<string>();
	let runtimeIndent: number | null = null;
	let role: string | null = null;
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

export type ComposeFile = readonly [label: string, text: string];

/** A service that sets a role-scoped variable, named the way a failure has to name it. */
interface Delivery {
	readonly id: string;
	readonly service: ComposeService;
}

/**
 * Every service that sets one variable. The scope hangs off the variable rather than off each
 * delivery because it is a property of the setting, not of who happened to forward it first.
 */
interface DeliveredVariable {
	readonly scope: RoleScope;
	readonly deliveries: Delivery[];
}

interface Analysis {
	readonly failures: string[];
	readonly delivered: ReadonlyMap<string, DeliveredVariable>;
	/** Ids of the services running the application image, so a caller can check the set was found. */
	readonly applicationContainers: readonly string[];
}

export function analyse(
	applicationText: string,
	compose: readonly ComposeFile[],
	profileRoles: ProfileRoles = new Map(),
): Analysis {
	const { paths, placeholders } = readApplicationConfig(applicationText);

	// Longest path first so a nested scope wins over its parent.
	const scopeOrder = [...ROLE_SCOPES].toSorted((a, b) => b.path.length - a.path.length);
	const ownership = new Map<string, RoleScope>();
	for (const [variable, path] of placeholders) {
		const scope = scopeOrder.find((s) => path === s.path || path.startsWith(`${s.path}.`));
		if (scope) ownership.set(variable, scope);
	}

	const failures: string[] = [];
	for (const scope of ROLE_SCOPES) {
		if (!paths.has(scope.path)) {
			failures.push(
				`ROLE_SCOPES declares "${scope.path}" (${scope.role} role), which ${APPLICATION_YML} does not have.\n` +
					"  Point it at wherever the setting moved, or drop the entry — as written it checks nothing.",
			);
		}
	}

	const delivered = new Map<string, DeliveredVariable>();
	const applicationContainers: Delivery[] = [];
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
			if (service.image.includes(APPLICATION_IMAGE)) applicationContainers.push({ id, service });
			for (const variable of service.env) {
				const scope = ownership.get(variable);
				if (!scope) continue;
				const record = delivered.get(variable) ?? { scope, deliveries: [] };
				delivered.set(variable, record);
				record.deliveries.push({ id, service });
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

	for (const [variable, { scope, deliveries }] of delivered) {
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

	// The other half of ROLE_SCOPES. A setting nothing gates on a role is read by every container
	// running the application image, so the value one of them is handed is a claim about the whole
	// deployment — and two containers making different claims describe a stack that does not exist.
	// The release lock's agent digest reached the app containers and not the receiver that way; the
	// receiver derived a tag from its own version instead and the pin guard refused to boot on it.
	const spellings = new Map<string, Map<string, string[]>>();
	for (const { id, service } of applicationContainers) {
		for (const [variable, value] of service.raw) {
			if (PER_CONTAINER.has(variable)) continue;
			const byValue = spellings.get(variable) ?? new Map<string, string[]>();
			spellings.set(variable, byValue);
			byValue.set(value, [...(byValue.get(value) ?? []), id]);
		}
	}
	for (const [variable, byValue] of spellings) {
		if (byValue.size < 2) continue;
		const written = [...byValue]
			.map(([value, ids]) => `    ${value === "" ? "<nothing>" : value}\n      ${ids.join(", ")}`)
			.join("\n");
		failures.push(
			`Containers running the application image disagree on ${variable}:\n${written}\n` +
				"  No runtime role gates this setting, so every one of them reads it and they cannot both\n" +
				"  be describing the same deployment.\n" +
				`  Give them one value, or name ${variable} in PER_CONTAINER with the reason it differs.`,
		);
	}

	// The disagreement above only sees containers that mention the key. A container that omits it
	// makes no claim to disagree with, and reads the application default instead — which for the
	// agent image is a tag derived from its own version, the exact thing the guards refuse. So an
	// omission is a value here, not an absence.
	for (const { variable, path, why } of DEPLOYMENT_WIDE) {
		if (!paths.has(path)) {
			failures.push(
				`DEPLOYMENT_WIDE declares "${path}" (${variable}), which ${APPLICATION_YML} does not have.\n` +
					"  Point it at wherever the setting moved, or drop the entry — as written it checks nothing.",
			);
			continue;
		}
		const missing = applicationContainers.filter(({ service }) => !service.env.has(variable));
		if (missing.length === 0) continue;
		const listed = missing.map(({ id }) => `    ${id}`).join("\n");
		failures.push(
			missing.length === applicationContainers.length
				? `${variable} binds ${path}, and no container running the application image is given it.\n` +
						`  ${why}.\n` +
						"  Every one of them reads it, so the deployment has nowhere to get the value from."
				: `${variable} binds ${path}, and these containers run the application image without it:\n${listed}\n` +
						`  ${why}.\n` +
						"  The setting is not gated on a runtime role, so leaving it off one container does not\n" +
						"  scope it — that container falls back to the application default and reads a different\n" +
						"  deployment than its siblings.",
		);
	}

	return { failures, delivered, applicationContainers: applicationContainers.map((c) => c.id) };
}

if (process.argv[1] === import.meta.filename) {
	const compose: ComposeFile[] = [];
	for (const file of COMPOSE_FILES) {
		compose.push([file, await readFile(join(REPO_ROOT, file), "utf8")]);
	}
	const profileRoles = await readProfileRoles();
	const { failures, delivered, applicationContainers } = analyse(
		await readFile(join(REPO_ROOT, APPLICATION_YML), "utf8"),
		compose,
		profileRoles,
	);
	if (failures.length > 0) {
		for (const failure of failures) console.error(`${failure}\n`);
		process.exit(1);
	}
	console.log(
		`check-env-roles: ${delivered.size} role-scoped variable(s) reach a container that runs their role, ` +
			`and ${applicationContainers.length} application container(s) agree on every setting that is not role-scoped.`,
	);
}
