/**
 * Chart behaviour, read from what Helm actually renders.
 *
 * Each case runs `helm template` on the real chart, a real profile, values that kubernetes-chart.ts
 * prepares from a synthetic lock over the real release image inventory, and operator values that
 * name a Secret but hold no secret value. The assertions read the parsed resources. A render is not
 * a cluster: admission, network enforcement, volume start-up and upgrades stay with the cluster tests.
 *
 * The worker renders only with the names of what an operator qualified on the sandbox nodes. These
 * cases pass synthetic names: a render proves which names and which authority the worker receives,
 * never that a node loads a profile or enforces a process limit or a policy.
 */
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { parseAllDocuments, stringify } from "yaml";

import { parseInventory } from "./commit-image-lock.ts";
import { chartValues, renderChartValues } from "./kubernetes-chart.ts";
import { isRecord, readJsonFileSync } from "./lib/json.ts";
import { parseReleaseImageLock } from "./release-image-lock.ts";

const chart = fileURLToPath(new URL("../deploy/chart/hephaestus/", import.meta.url));
const profile = (name: string) => path.join(chart, "profiles", `${name}.yaml`);

/** A stalled helm process fails the case instead of holding the run. */
const VERSION_TIMEOUT_MS = 10_000;
const RENDER_TIMEOUT_MS = 30_000;

const helm = spawnSync("helm", ["version", "--short"], {
	encoding: "utf8",
	timeout: VERSION_TIMEOUT_MS,
});
if (helm.error !== undefined || helm.status !== 0) {
	throw new Error(
		`The chart render tests need the helm executable on PATH, and it could not run (${helm.error?.message ?? `exit ${String(helm.status)}`}). Install the Helm version that .github/actions/setup-helm pins, then run vp run test:kubernetes:chart again.`,
	);
}

const inventory = parseInventory(
	readJsonFileSync(fileURLToPath(new URL("../security/release-images.json", import.meta.url))),
);
const digest = (character: string) => `sha256:${character.repeat(64)}`;
const platforms = { "linux/amd64": digest("a"), "linux/arm64": digest("b") };
const prepared = chartValues(
	parseReleaseImageLock({
		schemaVersion: 1,
		release: "v1.2.3",
		commit: "c".repeat(40),
		images: [
			...inventory.images.map((image, index) => ({
				image,
				repository: `ghcr.io/hephaestus-build/${image}`,
				provenance: "first-party",
				indexDigest: digest(String(index)),
				platforms,
			})),
			...inventory.upstream.map((entry) => ({
				image: entry.name,
				repository: entry.repository,
				provenance: "upstream",
				indexDigest: entry.digest,
				platforms,
			})),
		],
	}),
	inventory,
);

const secretName = "hephaestus-secrets";
const gatewayPods = { "gateway.networking.k8s.io/gateway-name": "edge" };
const parentRefs = [{ name: "edge", namespace: "gateway-system", sectionName: "https" }];
const operator = {
	host: "hephaestus.example.com",
	trustedProxies: String.raw`10\.42\.\d{1,3}\.\d{1,3}`,
	secret: { name: secretName },
	route: { parentRefs },
	networkPolicy: { gateway: { namespace: "gateway-system", podLabels: gatewayPods } },
	postgresql: { tls: { existingSecret: "hephaestus-postgresql-tls" } },
};
const external = {
	postgresql: { external: { host: "db.internal", cidr: "10.0.12.0/24" } },
	nats: { external: { url: "nats://nats.internal:4222", cidr: "10.0.13.0/24" } },
};
/** Synthetic cluster prerequisites: the required process limit and both optional names. */
const workerRuntime = {
	runtimeClassName: "gvisor",
	seccompProfile: "hephaestus/agent-seccomp-profile.json",
	podPidsLimit: 256,
};
/** The API server as the network plugin sees it after the Service address is translated. */
const kubernetesApi = { cidr: "192.168.5.15/32", port: 6443 };
const workerLayer = {
	worker: { enabled: true, runtime: workerRuntime },
	networkPolicy: { kubernetesApi },
};

type Resource = Record<string, unknown>;
/** A values file path, or values to write to a file. */
type Layer = string | object;
interface Rendered {
	ok: boolean;
	stderr: string;
	resources: Resource[];
}

/** One `helm template` run. A string layer is a values file path; an object is written to one. */
function render(
	layers: readonly Layer[],
	{ release = "heph", namespace = "hephaestus" } = {},
): Rendered {
	const directory = mkdtempSync(path.join(tmpdir(), "hephaestus-chart-"));
	try {
		const files = layers.map((layer, index) => {
			if (typeof layer === "string") {
				return layer;
			}
			const file = path.join(directory, `values-${index}.yaml`);
			// The prepared values reach Helm as the bytes prepare-release-lock writes, not a re-encoding.
			writeFileSync(file, layer === prepared ? renderChartValues(prepared) : stringify(layer));
			return file;
		});
		const result = spawnSync(
			"helm",
			[
				"template",
				release,
				chart,
				"--namespace",
				namespace,
				...files.flatMap((file) => ["--values", file]),
			],
			{ encoding: "utf8", timeout: RENDER_TIMEOUT_MS },
		);
		// A spawn failure or a timeout is a failed run, never a refusal to assert on.
		if (result.error !== undefined) {
			throw result.error;
		}
		if (result.signal !== null) {
			throw new Error(`helm template stopped with ${result.signal}`);
		}
		if (result.status !== 0) {
			return { ok: false, stderr: result.stderr, resources: [] };
		}
		const documents = parseAllDocuments(result.stdout);
		const resources: Resource[] = [];
		for (const document of documents) {
			assert.deepEqual(document.errors, [], "helm rendered invalid YAML");
			const value: unknown = document.toJS();
			if (isRecord(value)) {
				resources.push(value);
			}
		}
		return { ok: true, stderr: result.stderr, resources };
	} finally {
		rmSync(directory, { recursive: true, force: true });
	}
}

function rendered(layers: readonly Layer[], options = {}): Resource[] {
	const result = render(layers, options);
	assert.ok(result.ok, result.stderr);
	return result.resources;
}

const get = (value: unknown, ...keys: (string | number)[]): unknown => {
	let current = value;
	for (const key of keys) {
		if (typeof key === "number") {
			current = Array.isArray(current) ? current[key] : undefined;
		} else {
			current = isRecord(current) ? current[key] : undefined;
		}
	}
	return current;
};
const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
const kindOf = (resource: Resource) => get(resource, "kind");
const nameOf = (resource: Resource) => String(get(resource, "metadata", "name"));
const componentOf = (resource: Resource) =>
	get(resource, "metadata", "labels", "app.kubernetes.io/component");
const workloads = (resources: Resource[]) =>
	resources.filter((resource) => ["Deployment", "StatefulSet"].includes(String(kindOf(resource))));
const workload = (resources: Resource[], component: string) => {
	const found = workloads(resources).filter((resource) => componentOf(resource) === component);
	assert.equal(found.length, 1, `one ${component} workload`);
	return found[0];
};
const podSpec = (resource: unknown) => get(resource, "spec", "template", "spec");
const containers = (resource: unknown) => [
	...list(get(podSpec(resource), "initContainers")),
	...list(get(podSpec(resource), "containers")),
];
const container = (resource: unknown) => {
	const found = list(get(podSpec(resource), "containers"));
	assert.equal(found.length, 1);
	return found[0];
};
const env = (resource: unknown, name: string) =>
	list(get(container(resource), "env")).find((entry) => get(entry, "name") === name);
const envValue = (resource: unknown, name: string) => get(env(resource, name), "value");
/** Code-point order, the same on every platform and locale. */
const byCodePoint = (left: string, right: string) => (left < right ? -1 : Number(left > right));
/** A non-empty selector whose every label the given label set carries. */
const selects = (selector: unknown, labels: unknown) =>
	isRecord(selector) &&
	isRecord(labels) &&
	Object.keys(selector).length > 0 &&
	Object.entries(selector).every(([key, value]) => labels[key] === value);
const named = (resources: Resource[], kind: string, name: unknown) =>
	resources.some((resource) => kindOf(resource) === kind && nameOf(resource) === name);

/** The name and every label value fit Kubernetes' limits, and the full release stays a label. */
function assertBounded(resource: Resource, release: string, label: string): void {
	assert.equal(get(resource, "metadata", "labels", "app.kubernetes.io/instance"), release, label);
	assert.ok(nameOf(resource).length <= 63, label);
	for (const labels of [
		get(resource, "metadata", "labels"),
		get(resource, "spec", "template", "metadata", "labels"),
	].filter(isRecord)) {
		for (const value of Object.values(labels)) {
			assert.ok(String(value).length <= 63, `${label} label ${String(value)}`);
		}
	}
	if (kindOf(resource) === "StatefulSet") {
		// The controller adds controller-revision-hash (the name, a dash and up to 10 characters)
		// and statefulset.kubernetes.io/pod-name ("<name>-0") as label values.
		assert.ok(nameOf(resource).length + 1 + 10 <= 63, label);
	}
}

/** A workload selects its own pods, and its account, claims and config maps are rendered objects. */
function assertWorkloadReferences(resources: Resource[], resource: Resource, label: string): void {
	assert.ok(
		selects(
			get(resource, "spec", "selector", "matchLabels"),
			get(resource, "spec", "template", "metadata", "labels"),
		),
		`${label} selector`,
	);
	const spec = podSpec(resource);
	// PostgreSQL and NATS run under the namespace default account, without its token.
	const account = get(spec, "serviceAccountName");
	assert.ok(
		account === undefined || named(resources, "ServiceAccount", account),
		`${label} service account`,
	);
	for (const volume of list(get(spec, "volumes"))) {
		const claim = get(volume, "persistentVolumeClaim", "claimName");
		const config = get(volume, "configMap", "name");
		assert.ok(
			claim === undefined || named(resources, "PersistentVolumeClaim", claim),
			`${label} claim`,
		);
		assert.ok(config === undefined || named(resources, "ConfigMap", config), `${label} config map`);
	}
}

/** Every name a resource refers to is a rendered object, and every selector selects a rendered pod. */
function assertReferencesResolve(
	resources: Resource[],
	podLabels: readonly unknown[],
	resource: Resource,
	label: string,
): void {
	const kind = kindOf(resource);
	if (kind === "StatefulSet") {
		assert.ok(
			named(resources, "Service", get(resource, "spec", "serviceName")),
			`${label} serviceName`,
		);
	}
	if (kind === "Deployment" || kind === "StatefulSet") {
		assertWorkloadReferences(resources, resource, label);
	}
	if (kind === "Service") {
		const selector = get(resource, "spec", "selector");
		assert.ok(
			podLabels.some((labels) => selects(selector, labels)),
			`${label} selects no pod`,
		);
	}
	// A sandbox namespace's policies select the Pods that the worker's runtime creates there.
	if (kind === "NetworkPolicy" && get(resource, "metadata", "namespace") === undefined) {
		const selector = get(resource, "spec", "podSelector", "matchLabels");
		assert.ok(
			podLabels.some((labels) => selects(selector, labels)),
			`${label} selects no pod`,
		);
	}
	if (kind === "RoleBinding") {
		const namespace = get(resource, "metadata", "namespace");
		assert.ok(
			resources.some(
				(candidate) =>
					kindOf(candidate) === "Role" &&
					nameOf(candidate) === get(resource, "roleRef", "name") &&
					get(candidate, "metadata", "namespace") === namespace,
			),
			`${label} role`,
		);
		for (const subject of list(get(resource, "subjects"))) {
			assert.ok(named(resources, "ServiceAccount", get(subject, "name")), `${label} subject`);
		}
	}
	if (kind === "HTTPRoute") {
		for (const backend of list(get(resource, "spec", "rules")).flatMap((rule) =>
			list(get(rule, "backendRefs")),
		)) {
			assert.ok(named(resources, "Service", get(backend, "name")), `${label} backend`);
		}
	}
}

const profiles = ["dev", "preview", "small", "cluster"] as const;
const maxFileBytes = {
	dev: 4_294_967_296,
	preview: 2_147_483_648,
	small: 17_179_869_184,
	cluster: 17_179_869_184,
};
const layersFor = (name: (typeof profiles)[number]) => [
	profile(name),
	prepared,
	operator,
	...(name === "cluster" ? [external] : []),
];
const bundled = (name: (typeof profiles)[number]) => name !== "cluster";
const imageOf = {
	server: "application-server",
	webhook: "application-server",
	webapp: "webapp",
	postgresql: "postgres",
	nats: "nats",
} as const;

for (const name of profiles) {
	await test(`${name}: each role runs its locked image with its own role flags`, () => {
		const resources = rendered(layersFor(name));
		const components = workloads(resources)
			.map((resource) => String(componentOf(resource)))
			.toSorted(byCodePoint);
		assert.deepEqual(
			components,
			bundled(name)
				? ["nats", "postgresql", "server", "webapp", "webhook"]
				: ["server", "webapp", "webhook"],
		);
		for (const [component, image] of Object.entries(imageOf)) {
			if (!components.includes(component)) {
				continue;
			}
			for (const entry of containers(workload(resources, component))) {
				assert.equal(get(entry, "image"), prepared.images[image], `${component} image`);
			}
		}
		const server = workload(resources, "server");
		const webhook = workload(resources, "webhook");
		assert.equal(envValue(server, "SPRING_PROFILES_ACTIVE"), "prod");
		assert.equal(envValue(server, "HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED"), "false");
		assert.equal(envValue(server, "HEPHAESTUS_RUNTIME_WORKER_ENABLED"), "false");
		assert.equal(env(server, "SPRING_LIQUIBASE_ENABLED"), undefined, "the server owns migrations");
		assert.equal(get(server, "spec", "replicas"), 1);
		assert.equal(get(server, "spec", "strategy", "type"), "Recreate");
		assert.equal(envValue(webhook, "SPRING_PROFILES_ACTIVE"), "prod,webhook");
		assert.equal(envValue(webhook, "HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED"), "true");
		assert.equal(envValue(webhook, "HEPHAESTUS_RUNTIME_SERVER_ENABLED"), "false");
		assert.equal(envValue(webhook, "HEPHAESTUS_RUNTIME_WORKER_ENABLED"), "false");
		assert.equal(envValue(webhook, "SPRING_LIQUIBASE_ENABLED"), "false");
		for (const role of [server, webhook]) {
			assert.equal(envValue(role, "HEPHAESTUS_AGENT_IMAGE_REFERENCE"), prepared.images["agent-pi"]);
			assert.equal(
				envValue(role, "HEPHAESTUS_IMAGE_APPLICATION_SERVER"),
				prepared.images["application-server"],
			);
			assert.equal(envValue(role, "HEPHAESTUS_RELEASE_COMMIT"), prepared.release.commit);
			assert.equal(envValue(role, "APP_VERSION"), "1.2.3");
		}
		// No second migration owner, no cluster-wide authority, no worker objects.
		for (const resource of resources) {
			assert.ok(
				![
					"Job",
					"CronJob",
					"Role",
					"RoleBinding",
					"ClusterRole",
					"ClusterRoleBinding",
					"Namespace",
					"Ingress",
					"Gateway",
					"Secret",
				].includes(String(kindOf(resource))),
				`${String(kindOf(resource))} ${nameOf(resource)}`,
			);
			const annotations = get(resource, "metadata", "annotations");
			assert.ok(!isRecord(annotations) || !("helm.sh/hook" in annotations), nameOf(resource));
			assert.notEqual(componentOf(resource), "worker", nameOf(resource));
		}
	});

	await test(`${name}: every pod is restricted, tokenless and bounded, and reads secrets by reference`, () => {
		const resources = rendered(layersFor(name));
		for (const account of resources.filter((resource) => kindOf(resource) === "ServiceAccount")) {
			assert.equal(get(account, "automountServiceAccountToken"), false, nameOf(account));
		}
		for (const resource of workloads(resources)) {
			const label = nameOf(resource);
			const spec = podSpec(resource);
			assert.equal(get(spec, "automountServiceAccountToken"), false, label);
			assert.equal(get(spec, "securityContext", "runAsNonRoot"), true, label);
			assert.notEqual(get(spec, "securityContext", "runAsUser"), 0, label);
			assert.equal(get(spec, "securityContext", "seccompProfile", "type"), "RuntimeDefault", label);
			for (const flag of ["hostNetwork", "hostPID", "hostIPC"]) {
				assert.notEqual(get(spec, flag), true, `${label} ${flag}`);
			}
			assert.equal(get(spec, "securityContext", "sysctls"), undefined, label);
			for (const volume of list(get(spec, "volumes"))) {
				assert.equal(get(volume, "hostPath"), undefined, label);
			}
			for (const entry of containers(resource)) {
				const security = get(entry, "securityContext");
				assert.equal(get(security, "allowPrivilegeEscalation"), false, label);
				assert.equal(get(security, "readOnlyRootFilesystem"), true, label);
				assert.notEqual(get(security, "privileged"), true, label);
				assert.deepEqual(get(security, "capabilities", "drop"), ["ALL"], label);
				assert.equal(get(security, "capabilities", "add"), undefined, label);
				for (const bound of ["requests", "limits"]) {
					for (const resourceName of ["cpu", "memory", "ephemeral-storage"]) {
						assert.equal(
							typeof get(entry, "resources", bound, resourceName),
							"string",
							`${label} ${bound}.${resourceName}`,
						);
					}
				}
				for (const variable of list(get(entry, "env"))) {
					const variableName = String(get(variable, "name"));
					// The review-ledger identity stays per process: no rendered role overrides it.
					assert.notEqual(variableName, "HEPHAESTUS_WORKER_ID", label);
					const reference = get(variable, "valueFrom", "secretKeyRef");
					if (reference !== undefined) {
						assert.equal(get(reference, "name"), secretName, variableName);
					} else if (
						/(?:SECRET|PASSWORD|TOKEN|PRIVATE_KEY|ENCRYPTION_KEY|COOKIE_KEY)$/u.test(variableName)
					) {
						assert.fail(`${label} sets ${variableName} without the Secret`);
					}
				}
			}
		}
	});

	await test(`${name}: storage is bounded, and one value sizes the broker and the stream budget`, () => {
		const resources = rendered(layersFor(name));
		const budget = maxFileBytes[name];
		assert.equal(
			envValue(workload(resources, "webhook"), "HEPHAESTUS_WEBHOOK_STREAM_STORAGE_BUDGET"),
			String(budget),
		);
		const claim = resources.find((resource) => kindOf(resource) === "PersistentVolumeClaim");
		assert.match(
			String(get(claim, "spec", "resources", "requests", "storage")),
			/^[1-9]\d*(?:Gi)$/u,
		);
		if (!bundled(name)) {
			return;
		}
		const config = resources.find(
			(resource) => kindOf(resource) === "ConfigMap" && componentOf(resource) === "nats",
		);
		assert.match(
			String(get(config, "data", "nats-server.conf")),
			new RegExp(`^\\s*max_file: ${budget}$`, "mu"),
		);
		const nats = workload(resources, "nats");
		assert.equal(get(nats, "spec", "replicas"), 1);
		assert.equal(
			String(
				get(nats, "spec", "volumeClaimTemplates", 0, "spec", "resources", "requests", "storage"),
			),
			String(Math.floor((budget * 5) / 4)),
		);
		// ADR 0038: the volume sits at /var/lib/postgresql; the image keeps its own versioned PGDATA.
		const postgresql = workload(resources, "postgresql");
		assert.equal(get(postgresql, "spec", "replicas"), 1);
		assert.equal(env(postgresql, "PGDATA"), undefined);
		const mounts = list(get(container(postgresql), "volumeMounts"));
		assert.deepEqual(mounts.map((mount) => String(get(mount, "mountPath"))).toSorted(byCodePoint), [
			"/etc/postgresql/tls",
			"/tmp",
			"/var/lib/postgresql",
			"/var/run/postgresql",
		]);
		assert.equal(get(postgresql, "spec", "volumeClaimTemplates", 0, "metadata", "name"), "data");
		assert.equal(get(postgresql, "spec", "template", "spec", "securityContext", "fsGroup"), 999);
	});
}

await test("the webapp runs the non-root image with one writable subtree on 8080", () => {
	const resources = rendered(layersFor("small"));
	const webapp = workload(resources, "webapp");
	const spec = podSpec(webapp);
	assert.equal(get(spec, "securityContext", "runAsUser"), 101);
	assert.equal(get(spec, "securityContext", "runAsGroup"), 101);
	assert.equal(get(spec, "securityContext", "fsGroup"), 101);
	assert.equal(get(spec, "initContainers"), undefined);
	const entry = container(webapp);
	assert.equal(get(entry, "command"), undefined);
	assert.equal(get(entry, "args"), undefined);
	assert.deepEqual(get(entry, "ports"), [{ name: "http", containerPort: 8080 }]);
	assert.deepEqual(get(entry, "volumeMounts"), [
		{ name: "runtime", mountPath: "/tmp/hephaestus-webapp" },
	]);
	assert.deepEqual(get(spec, "volumes"), [{ name: "runtime", emptyDir: { sizeLimit: "16Mi" } }]);
	for (const probe of ["startupProbe", "livenessProbe", "readinessProbe"]) {
		assert.deepEqual(get(entry, probe, "httpGet"), { path: "/", port: "http" }, probe);
	}
	const service = resources.find(
		(resource) => kindOf(resource) === "Service" && componentOf(resource) === "webapp",
	);
	assert.deepEqual(get(service, "spec", "ports"), [
		{ name: "http", port: 8080, targetPort: "http" },
	]);
});

await test("the route attaches to the platform Gateway and strips only /api", () => {
	const resources = rendered(layersFor("small"));
	const routes = resources.filter((resource) => kindOf(resource) === "HTTPRoute");
	assert.equal(routes.length, 1);
	const route = routes[0];
	assert.deepEqual(get(route, "spec", "parentRefs"), parentRefs);
	assert.deepEqual(get(route, "spec", "hostnames"), [operator.host]);
	const rules = list(get(route, "spec", "rules"));
	const rule = (type: string, value: string) => {
		const found = rules.filter((candidate) =>
			list(get(candidate, "matches")).some(
				(match) => get(match, "path", "type") === type && get(match, "path", "value") === value,
			),
		);
		assert.equal(found.length, 1, `${type} ${value}`);
		return found[0];
	};
	const filters = (candidate: unknown) => list(get(candidate, "filters"));
	const backends = (candidate: unknown) => get(candidate, "backendRefs");
	assert.equal(rules.length, 4);
	assert.deepEqual(backends(rule("Exact", "/webhooks/slack/interactivity")), [
		{ name: "heph-server", port: 8080 },
	]);
	assert.deepEqual(backends(rule("PathPrefix", "/webhooks")), [
		{ name: "heph-webhook", port: 8080 },
	]);
	assert.deepEqual(backends(rule("PathPrefix", "/api")), [{ name: "heph-server", port: 8080 }]);
	assert.deepEqual(backends(rule("PathPrefix", "/")), [{ name: "heph-webapp", port: 8080 }]);
	assert.deepEqual(
		filters(rule("PathPrefix", "/api")).filter((filter) => get(filter, "type") === "URLRewrite"),
		[
			{
				type: "URLRewrite",
				urlRewrite: { path: { type: "ReplacePrefixMatch", replacePrefixMatch: "/" } },
			},
		],
	);
	for (const candidate of rules) {
		const types = new Set(filters(candidate).map((filter) => get(filter, "type")));
		assert.ok(types.has("ResponseHeaderModifier"), "HSTS on every rule");
		assert.equal(
			types.has("URLRewrite"),
			candidate === rule("PathPrefix", "/api"),
			"only /api is rewritten",
		);
		// Only /api asks the controller to disable both deadlines, for the Heph live stream.
		assert.deepEqual(
			get(candidate, "timeouts"),
			candidate === rule("PathPrefix", "/api")
				? { request: "0s", backendRequest: "0s" }
				: undefined,
			"only /api sets timeouts",
		);
		assert.equal(get(candidate, "retry"), undefined, "no rule retries");
	}
	const hsts = filters(rules[0]).find((filter) => get(filter, "type") === "ResponseHeaderModifier");
	assert.deepEqual(get(hsts, "responseHeaderModifier", "set"), [
		{ name: "Strict-Transport-Security", value: "max-age=2592000; includeSubDomains" },
	]);
	// The Services the route names exist and listen on the same port.
	for (const backend of rules.flatMap((candidate) => list(backends(candidate)))) {
		const service = resources.find(
			(resource) => kindOf(resource) === "Service" && nameOf(resource) === get(backend, "name"),
		);
		assert.equal(get(service, "spec", "ports", 0, "port"), get(backend, "port"));
	}
});

for (const name of ["small", "cluster"] as const) {
	await test(`${name}: network policy denies by default and names exact peers`, () => {
		const resources = rendered(layersFor(name));
		const policies = resources.filter((resource) => kindOf(resource) === "NetworkPolicy");
		const policy = (component: string) => {
			const found = policies.find((candidate) => nameOf(candidate) === `heph-${component}`);
			assert.ok(found, component);
			return found;
		};
		const deny = policy("default-deny");
		assert.deepEqual(get(deny, "spec"), {
			podSelector: {
				matchLabels: {
					"app.kubernetes.io/name": "hephaestus",
					"app.kubernetes.io/instance": "heph",
				},
			},
			policyTypes: ["Ingress", "Egress"],
		});
		for (const candidate of policies) {
			for (const rule of [
				...list(get(candidate, "spec", "ingress")),
				...list(get(candidate, "spec", "egress")),
			]) {
				for (const peer of [...list(get(rule, "from")), ...list(get(rule, "to"))]) {
					if (get(peer, "ipBlock") !== undefined) {
						continue;
					}
					const selector = get(peer, "podSelector");
					const labels = get(selector, "matchLabels");
					assert.ok(
						isRecord(labels) && Object.keys(labels).length > 0,
						`${nameOf(candidate)} admits a whole namespace`,
					);
				}
			}
		}
		const gatewayPeer = {
			namespaceSelector: { matchLabels: { "kubernetes.io/metadata.name": "gateway-system" } },
			podSelector: { matchLabels: gatewayPods },
		};
		for (const [component, port] of [
			["server", "http"],
			["webhook", "http"],
			["webapp", "http"],
		] as const) {
			const ingress = list(get(policy(component), "spec", "ingress"));
			assert.equal(ingress.length, 1, `${component}: no scraper is configured`);
			assert.deepEqual(get(ingress[0], "from", 0), gatewayPeer, component);
			assert.deepEqual(get(ingress[0], "ports"), [{ port, protocol: "TCP" }], component);
		}
		const webhookEgress = list(get(policy("webhook"), "spec", "egress"));
		const serverEgress = list(get(policy("server"), "spec", "egress"));
		if (name === "cluster") {
			for (const egress of [serverEgress, webhookEgress]) {
				assert.ok(
					egress.some(
						(rule) =>
							get(rule, "to", 0, "ipBlock", "cidr") === "10.0.12.0/24" &&
							get(rule, "ports", 0, "port") === 5432,
					),
				);
				assert.ok(
					egress.some(
						(rule) =>
							get(rule, "to", 0, "ipBlock", "cidr") === "10.0.13.0/24" &&
							get(rule, "ports", 0, "port") === 4222,
					),
				);
			}
		}
		// The receiver reaches the database and the broker only. The server adds internet HTTPS,
		// which never includes private ranges.
		assert.equal(webhookEgress.length, 2);
		const internet = serverEgress.filter(
			(rule) => get(rule, "to", 0, "ipBlock", "cidr") === "0.0.0.0/0",
		);
		assert.equal(internet.length, 1);
		assert.deepEqual(get(internet[0], "ports"), [{ port: 443, protocol: "TCP" }]);
		assert.ok(list(get(internet[0], "to", 0, "ipBlock", "except")).includes("10.0.0.0/8"));
	});
}

await test("admin assurance follows the operator's value and defaults to on", () => {
	const server = (layers: readonly Layer[]) => workload(rendered(layers), "server");
	assert.equal(envValue(server(layersFor("small")), "HEPHAESTUS_AUTH_ASSURANCE_ENFORCED"), "true");
	assert.equal(
		envValue(server(layersFor("preview")), "HEPHAESTUS_AUTH_ASSURANCE_ENFORCED"),
		"true",
	);
	assert.equal(
		envValue(
			server([...layersFor("small"), { config: { auth: { assuranceEnforced: false } } }]),
			"HEPHAESTUS_AUTH_ASSURANCE_ENFORCED",
		),
		"false",
	);
	assert.equal(
		env(workload(rendered(layersFor("small")), "webhook"), "HEPHAESTUS_AUTH_ASSURANCE_ENFORCED"),
		undefined,
	);
});

await test("the longest release names render bounded, distinct and consistent objects", () => {
	// Helm accepts release names of up to 53 characters. These two differ only in the last one.
	const releases = [`${"r".repeat(52)}a`, `${"r".repeat(52)}b`];
	const names = new Map<string, Set<string>>();
	for (const release of releases) {
		const resources = rendered([...layersFor("small"), workerLayer], { release, namespace: "h" });
		const podLabels = workloads(resources).map((resource) => {
			const labels = get(resource, "spec", "template", "metadata", "labels");
			// The StatefulSet controller names its first pod "<name>-0" and labels it with that name.
			return kindOf(resource) === "StatefulSet" && isRecord(labels)
				? { ...labels, "statefulset.kubernetes.io/pod-name": `${nameOf(resource)}-0` }
				: labels;
		});
		// Sandbox objects share fixed names, each in the release's own sandbox namespace.
		names.set(
			release,
			new Set(
				resources.map((resource) => {
					const namespace = get(resource, "metadata", "namespace");
					assert.ok(
						namespace === undefined || typeof namespace === "string",
						"a resource namespace is a string when set",
					);
					return `${String(kindOf(resource))}/${namespace ?? ""}/${nameOf(resource)}`;
				}),
			),
		);
		for (const resource of resources) {
			const label = `${String(kindOf(resource))} ${nameOf(resource)}`;
			// The full release identity stays in the labels, whatever the name became.
			assertBounded(resource, release, label);
			assertReferencesResolve(resources, podLabels, resource, label);
		}
		// The roles reach the bundled services under the names those Services received.
		for (const role of [workload(resources, "server"), workload(resources, "webhook")]) {
			const database = /^postgresql:\/\/(?<service>[^.:/]+)\.h\.svc:5432\//u.exec(
				String(envValue(role, "DATABASE_URL")),
			)?.groups?.service;
			assert.ok(named(resources, "Service", database), `database host ${String(database)}`);
		}
		const broker = /^nats:\/\/(?<host>[^:/]+):4222$/u.exec(
			String(envValue(workload(resources, "server"), "NATS_SERVER")),
		)?.groups?.host;
		assert.ok(named(resources, "Service", broker), `broker host ${String(broker)}`);
		// The worker's one pod name fits a label, and its gateway policy names it.
		const worker = workload(resources, "worker");
		assert.ok(worker, "one worker workload");
		const workerPod = `${nameOf(worker)}-0`;
		assert.ok(workerPod.length <= 63, workerPod);
		assert.ok(named(resources, "NetworkPolicy", `gateway-${workerPod}`), "gateway policy");
	}
	const [first = new Set<string>(), second = new Set<string>()] = releases.map(
		(release) => names.get(release) ?? new Set<string>(),
	);
	assert.equal(first.size, second.size);
	for (const name of first) {
		assert.equal(second.has(name), false, `both releases render ${name}`);
	}
});

/** Each grant of a Role, as `apiGroup/resource[names]:verb`, in code-point order. */
const grants = (role: unknown) =>
	list(get(role, "rules"))
		.flatMap((rule) => {
			const names = list(get(rule, "resourceNames"));
			const suffix = names.length > 0 ? `[${names.map(String).join(",")}]` : "";
			return list(get(rule, "apiGroups")).flatMap((group) =>
				list(get(rule, "resources")).flatMap((resource) =>
					list(get(rule, "verbs")).map(
						(verb) => `${String(group)}/${String(resource)}${suffix}:${String(verb)}`,
					),
				),
			);
		})
		.toSorted(byCodePoint);

await test("an enabled worker receives its runtime settings, its sandbox namespace and only the authority its runtime uses", () => {
	const resources = rendered([...layersFor("small"), workerLayer]);
	const worker = workload(resources, "worker");
	assert.ok(worker, "one worker workload");
	const workerName = nameOf(worker);
	const workerPod = `${workerName}-0`;
	const namespaces = resources.filter((resource) => kindOf(resource) === "Namespace");
	assert.equal(namespaces.length, 1, "one sandbox namespace");
	const [sandboxNamespace] = namespaces;
	assert.ok(sandboxNamespace, "the sandbox namespace");
	assert.equal(componentOf(sandboxNamespace), "sandbox");
	assert.equal(
		get(sandboxNamespace, "metadata", "labels", "pod-security.kubernetes.io/enforce"),
		"restricted",
	);
	const sandboxes = nameOf(sandboxNamespace);
	assert.equal(sandboxes, "hephaestus-heph-sandboxes");

	// The selected settings, the same strings that the chart's objects use.
	assert.equal(envValue(worker, "SPRING_PROFILES_ACTIVE"), "prod,worker");
	assert.equal(envValue(worker, "HEPHAESTUS_SANDBOX_RUNTIME"), "kubernetes");
	assert.equal(envValue(worker, "HEPHAESTUS_SANDBOX_KUBERNETES_NAMESPACE"), sandboxes);
	for (const [variable, field] of [
		["POD_NAME", "metadata.name"],
		["POD_IP", "status.podIP"],
	] as const) {
		assert.equal(
			get(
				env(worker, `HEPHAESTUS_SANDBOX_KUBERNETES_${variable}`),
				"valueFrom",
				"fieldRef",
				"fieldPath",
			),
			field,
			variable,
		);
	}
	assert.deepEqual(
		{
			runtimeClassName: envValue(worker, "HEPHAESTUS_SANDBOX_KUBERNETES_RUNTIME_CLASS_NAME"),
			seccompProfile: envValue(worker, "HEPHAESTUS_SANDBOX_KUBERNETES_SECCOMP_PROFILE"),
			podPidsLimit: envValue(worker, "HEPHAESTUS_SANDBOX_KUBERNETES_POD_PIDS_LIMIT"),
		},
		{ ...workerRuntime, podPidsLimit: String(workerRuntime.podPidsLimit) },
	);
	assert.equal(envValue(worker, "SANDBOX_API_PORT"), "8081");
	assert.ok(
		list(get(container(worker), "ports")).some(
			(port) => get(port, "name") === "sandbox-gateway" && get(port, "containerPort") === 8081,
		),
	);
	assert.equal(
		env(worker, "HEPHAESTUS_WORKER_ID"),
		undefined,
		"the ledger identity stays per process",
	);
	assert.equal(envValue(worker, "HEPHAESTUS_AGENT_IMAGE_REFERENCE"), prepared.images["agent-pi"]);
	assert.equal(get(container(worker), "image"), prepared.images["application-server"]);

	// One restricted worker pod with the installation labels that its sandboxes copy, and a token.
	assert.equal(get(worker, "spec", "replicas"), 1);
	const spec = podSpec(worker);
	assert.equal(get(spec, "serviceAccountName"), workerName);
	assert.equal(get(spec, "automountServiceAccountToken"), true);
	assert.equal(get(spec, "securityContext", "runAsNonRoot"), true);
	const security = get(container(worker), "securityContext");
	assert.equal(get(security, "allowPrivilegeEscalation"), false);
	assert.equal(get(security, "readOnlyRootFilesystem"), true);
	assert.deepEqual(get(security, "capabilities", "drop"), ["ALL"]);
	const labels = get(worker, "spec", "template", "metadata", "labels");
	for (const [key, value] of [
		["app.kubernetes.io/name", "hephaestus"],
		["app.kubernetes.io/part-of", "hephaestus"],
		["app.kubernetes.io/instance", "heph"],
		["app.kubernetes.io/component", "worker"],
	] as const) {
		assert.equal(get(labels, key), value, key);
	}

	// Sandbox pods run as a tokenless account of their namespace.
	const accounts = resources.filter(
		(resource) =>
			kindOf(resource) === "ServiceAccount" && get(resource, "metadata", "namespace") === sandboxes,
	);
	assert.deepEqual(accounts.map(nameOf), ["sandbox"]);
	assert.equal(get(accounts[0], "automountServiceAccountToken"), false);

	// Exactly the calls that the runtime makes, each in one namespace, and no cluster-wide authority.
	for (const kind of ["ClusterRole", "ClusterRoleBinding"]) {
		assert.equal(resources.filter((resource) => kindOf(resource) === kind).length, 0, kind);
	}
	const expected = new Map<string | undefined, string[]>([
		[
			sandboxes,
			[
				...["create", "delete", "get", "list", "patch", "watch"].map((verb) => `/pods:${verb}`),
				"/pods/exec:create",
				"/pods/exec:get",
				"/pods/log:get",
				...["create", "delete", "get", "list", "watch"].map((verb) => `batch/jobs:${verb}`),
			],
		],
	]);
	const roles = resources.filter((resource) => kindOf(resource) === "Role");
	const bindings = resources.filter((resource) => kindOf(resource) === "RoleBinding");
	assert.equal(roles.length, expected.size);
	assert.equal(bindings.length, expected.size);
	for (const [namespace, granted] of expected) {
		const label = namespace ?? "the release namespace";
		const role = roles.find((resource) => get(resource, "metadata", "namespace") === namespace);
		assert.ok(role, label);
		assert.deepEqual(grants(role), granted.toSorted(byCodePoint), label);
		const binding = bindings.find(
			(resource) => get(resource, "metadata", "namespace") === namespace,
		);
		assert.deepEqual(
			get(binding, "roleRef"),
			{ apiGroup: "rbac.authorization.k8s.io", kind: "Role", name: nameOf(role) },
			label,
		);
		assert.deepEqual(
			get(binding, "subjects"),
			[{ kind: "ServiceAccount", name: workerName, namespace: "hephaestus" }],
			label,
		);
	}

	// Turn sandboxes have no network. Reviews and the host reach this one worker's gateway only.
	const policiesIn = (namespace: string) =>
		resources
			.filter(
				(resource) =>
					kindOf(resource) === "NetworkPolicy" &&
					get(resource, "metadata", "namespace") === namespace,
			)
			.map(nameOf)
			.toSorted(byCodePoint);
	assert.deepEqual(policiesIn(sandboxes), ["default-deny", `gateway-${workerPod}`]);
	const gateway = resources.find(
		(resource) =>
			kindOf(resource) === "NetworkPolicy" && nameOf(resource) === `gateway-${workerPod}`,
	);
	assert.deepEqual(get(gateway, "spec", "podSelector"), {
		matchLabels: { "hephaestus.build/worker-pod": workerPod },
		matchExpressions: [
			{ key: "hephaestus.build/sandbox-kind", operator: "In", values: ["review", "host"] },
		],
	});
	assert.deepEqual(get(gateway, "spec", "egress"), [
		{
			to: [
				{
					namespaceSelector: { matchLabels: { "kubernetes.io/metadata.name": "hephaestus" } },
					podSelector: { matchLabels: { "statefulset.kubernetes.io/pod-name": workerPod } },
				},
			],
			ports: [{ port: 8081, protocol: "TCP" }],
		},
	]);
	const own = resources.find(
		(resource) =>
			kindOf(resource) === "NetworkPolicy" &&
			nameOf(resource) === workerName &&
			get(resource, "metadata", "namespace") === undefined,
	);
	assert.equal(
		get(own, "spec", "podSelector", "matchLabels", "statefulset.kubernetes.io/pod-name"),
		workerPod,
	);
	assert.equal(
		get(
			own,
			"spec",
			"ingress",
			0,
			"from",
			0,
			"namespaceSelector",
			"matchLabels",
			"kubernetes.io/metadata.name",
		),
		sandboxes,
	);
	assert.deepEqual(get(own, "spec", "ingress", 0, "ports"), [{ port: 8081, protocol: "TCP" }]);
	// The API only at the endpoint the operator qualified, as the plugin sees it after translation.
	const api = list(get(own, "spec", "egress")).filter(
		(rule) => get(rule, "to", 0, "ipBlock", "cidr") === kubernetesApi.cidr,
	);
	assert.deepEqual(api, [
		{
			to: [{ ipBlock: { cidr: kubernetesApi.cidr } }],
			ports: [{ port: kubernetesApi.port, protocol: "TCP" }],
		},
	]);
});

await test("a worker without the optional runtime names uses the cluster's default runtime and RuntimeDefault seccomp", () => {
	const resources = rendered([
		...layersFor("small"),
		{ worker: { enabled: true, runtime: { podPidsLimit: 256 } }, networkPolicy: { kubernetesApi } },
	]);
	const worker = workload(resources, "worker");
	assert.ok(worker, "one worker workload");
	assert.equal(envValue(worker, "HEPHAESTUS_SANDBOX_KUBERNETES_RUNTIME_CLASS_NAME"), "");
	assert.equal(envValue(worker, "HEPHAESTUS_SANDBOX_KUBERNETES_SECCOMP_PROFILE"), "");
	assert.equal(envValue(worker, "HEPHAESTUS_SANDBOX_KUBERNETES_POD_PIDS_LIMIT"), "256");
});

const caDirectory = "/etc/hephaestus/postgresql-ca";
const tlsSecret = operator.postgresql.tls.existingSecret;
const verifyFull = `sslmode=verify-full&sslrootcert=${caDirectory}/ca.crt`;

/** The role's only Secret volume holds ca.crt alone, mounted read-only where its URL names it. */
function assertCaOnly(role: Resource, label: string): void {
	assert.deepEqual(
		list(get(podSpec(role), "volumes")).filter((volume) => get(volume, "secret") !== undefined),
		[
			{
				name: "postgresql-ca",
				secret: {
					secretName: tlsSecret,
					items: [{ key: "ca.crt", path: "ca.crt" }],
					defaultMode: 292,
				},
			},
		],
		label,
	);
	assert.deepEqual(
		list(get(container(role), "volumeMounts")).filter(
			(mount) => get(mount, "name") === "postgresql-ca",
		),
		[{ name: "postgresql-ca", mountPath: caDirectory, readOnly: true }],
		label,
	);
}

await test("every role verifies the bundled PostgreSQL at its Service name with the CA alone", () => {
	const resources = rendered([...layersFor("small"), workerLayer]);
	const service = resources.find(
		(resource) => kindOf(resource) === "Service" && componentOf(resource) === "postgresql",
	);
	assert.ok(service, "the PostgreSQL Service");
	const url = `postgresql://${nameOf(service)}.hephaestus.svc:5432/hephaestus?${verifyFull}`;
	for (const component of ["server", "webhook", "worker"]) {
		const role = workload(resources, component);
		assert.ok(role, component);
		assert.equal(envValue(role, "DATABASE_URL"), url, component);
		assertCaOnly(role, component);
	}

	// The image's own entrypoint starts the server with TLS, from the certificate and key alone.
	const postgresql = workload(resources, "postgresql");
	assert.ok(postgresql, "postgresql");
	const entry = container(postgresql);
	assert.equal(get(entry, "command"), undefined);
	assert.deepEqual(get(entry, "args"), [
		"-c",
		"ssl=on",
		"-c",
		"ssl_cert_file=/etc/postgresql/tls/tls.crt",
		"-c",
		"ssl_key_file=/etc/postgresql/tls/tls.key",
	]);
	assert.deepEqual(
		list(get(entry, "volumeMounts")).filter((mount) => get(mount, "name") === "tls"),
		[{ name: "tls", mountPath: "/etc/postgresql/tls", readOnly: true }],
	);
	assert.deepEqual(
		list(get(podSpec(postgresql), "volumes")).filter(
			(volume) => get(volume, "secret") !== undefined,
		),
		[
			{
				name: "tls",
				secret: {
					secretName: tlsSecret,
					items: [
						{ key: "tls.crt", path: "tls.crt" },
						{ key: "tls.key", path: "tls.key" },
					],
					defaultMode: 288,
				},
			},
		],
	);
	const security = get(podSpec(postgresql), "securityContext");
	assert.deepEqual(
		[get(security, "runAsUser"), get(security, "runAsGroup"), get(security, "fsGroup")],
		[999, 999, 999],
	);
	assert.equal(get(entry, "securityContext", "readOnlyRootFilesystem"), true);
	// No other pod projects the server's key.
	assert.deepEqual(
		workloads(resources)
			.filter((resource) =>
				list(get(podSpec(resource), "volumes")).some((volume) =>
					list(get(volume, "secret", "items")).some((item) => get(item, "key") === "tls.key"),
				),
			)
			.map(componentOf),
		["postgresql"],
	);
});

await test("every role verifies an external PostgreSQL with the CA alone", () => {
	const resources = rendered(layersFor("cluster"));
	assert.equal(
		workloads(resources).some((resource) => componentOf(resource) === "postgresql"),
		false,
	);
	const url = `postgresql://${external.postgresql.external.host}:5432/hephaestus?${verifyFull}`;
	for (const component of ["server", "webhook"]) {
		const role = workload(resources, component);
		assert.ok(role, component);
		assert.equal(envValue(role, "DATABASE_URL"), url, component);
		assertCaOnly(role, component);
	}
});

await test("replica counts stop the database roles, start the server alone, and restore the release", () => {
	const roles = ["server", "webhook", "worker"] as const;
	const counts = (resources: Resource[]) =>
		roles.map((component) => get(workload(resources, component), "spec", "replicas"));
	const enabled = [...layersFor("small"), workerLayer];
	const running = rendered(enabled);
	assert.deepEqual(counts(running), [1, 1, 1]);

	// Stopped: the objects stay, so the worker keeps its StatefulSet, its claim and its custody.
	const stopped = rendered([
		...enabled,
		{ server: { replicas: 0 }, webhook: { replicas: 0 }, worker: { replicas: 0 } },
	]);
	assert.deepEqual(counts(stopped), [0, 0, 0]);
	assert.deepEqual(
		stopped.map((resource) => `${String(kindOf(resource))}/${nameOf(resource)}`),
		running.map((resource) => `${String(kindOf(resource))}/${nameOf(resource)}`),
	);
	const worker = workload(stopped, "worker");
	assert.equal(get(worker, "spec", "volumeClaimTemplates", 0, "metadata", "name"), "git-cache");
	assert.equal(get(workload(stopped, "server"), "spec", "strategy", "type"), "Recreate");
	for (const component of ["postgresql", "nats", "webapp"]) {
		assert.equal(get(workload(stopped, component), "spec", "replicas"), 1, component);
	}

	// The server migrates alone before the roles that read its schema start again.
	const migrating = rendered([...enabled, { webhook: { replicas: 0 }, worker: { replicas: 0 } }]);
	assert.deepEqual(counts(migrating), [1, 0, 0]);

	// Restoring the prior counts renders the release as it was.
	const restored = rendered([
		...enabled,
		{ server: { replicas: 1 }, webhook: { replicas: 1 }, worker: { replicas: 1 } },
	]);
	assert.deepEqual(restored, running);

	// A count does not create a worker that is not enabled.
	const disabled = rendered([...layersFor("small"), { worker: { replicas: 1 } }]);
	assert.equal(
		disabled.some((resource) => componentOf(resource) === "worker"),
		false,
	);
});

await test("values that the chart does not accept fail the render", () => {
	const withoutPrepared = [profile("small"), operator];
	const { host: _host, ...withoutHost } = operator;
	const { secret: _secret, ...withoutSecret } = operator;
	const { trustedProxies: _trustedProxies, ...withoutProxies } = operator;
	const worker = (change: object): Layer[] => [...layersFor("small"), workerLayer, change];
	const cases: [string, Layer[], RegExp][] = [
		[
			"an image tag",
			[...layersFor("small"), { images: { webapp: "ghcr.io/hephaestus-build/webapp:latest" } }],
			/images[./]webapp/u,
		],
		["values without the prepared lock file", withoutPrepared, /images|release/u],
		[
			"a generic environment hatch",
			[...layersFor("small"), { server: { extraEnv: [{ name: "X", value: "y" }] } }],
			/extraEnv/u,
		],
		[
			"a pod spec hatch",
			[...layersFor("small"), { webapp: { podSpec: { hostNetwork: true } } }],
			/podSpec/u,
		],
		[
			"network policy turned off",
			[...layersFor("small"), { networkPolicy: { enabled: false } }],
			/enabled/u,
		],
		["no Gateway parent", [...layersFor("small"), { route: { parentRefs: [] } }], /parentRefs/u],
		[
			"a Gateway parent without a name",
			[...layersFor("small"), { route: { parentRefs: [{ name: "" }] } }],
			/parentRefs[./]0[./]name/u,
		],
		["no host", [profile("small"), prepared, withoutHost], /host/u],
		["no Secret name", [profile("small"), prepared, withoutSecret], /secret[./]name/u],
		["no proxy trust", [profile("small"), prepared, withoutProxies], /trustedProxies/u],
		// Helm merges maps, so only null removes the labels that an earlier layer set.
		[
			"a whole Gateway namespace",
			[...layersFor("small"), { networkPolicy: { gateway: { podLabels: null } } }],
			/podLabels/u,
		],
		[
			"a whole scraper namespace",
			[
				...layersFor("small"),
				{ networkPolicy: { metrics: { namespace: "monitoring", podLabels: {} } } },
			],
			/networkPolicy\.metrics\.podLabels needs at least one label/u,
		],
		[
			"a zero CPU limit",
			[...layersFor("small"), { server: { resources: { limits: { cpu: "0" } } } }],
			/cpu/u,
		],
		[
			"an external database without a host",
			[
				profile("cluster"),
				prepared,
				operator,
				{ ...external, postgresql: { external: { cidr: "10.0.12.0/24" } } },
			],
			/postgresql\.external\.host is required/u,
		],
		[
			"a second server",
			[...layersFor("small"), { server: { replicas: 2 } }],
			/server[./]replicas/u,
		],
		["a fourth webhook receiver", worker({ webhook: { replicas: 4 } }), /webhook[./]replicas/u],
		["a negative webhook count", worker({ webhook: { replicas: -1 } }), /webhook[./]replicas/u],
		["a second worker", worker({ worker: { replicas: 2 } }), /worker[./]replicas/u],
		[
			"no PostgreSQL TLS Secret",
			[...layersFor("small"), { postgresql: { tls: { existingSecret: "" } } }],
			/postgresql\.tls\.existingSecret is required/u,
		],
		[
			"no PostgreSQL TLS Secret for an external database",
			[...layersFor("cluster"), { postgresql: { tls: { existingSecret: "" } } }],
			/postgresql\.tls\.existingSecret is required/u,
		],
		[
			"an external database without certificate verification",
			[...layersFor("cluster"), { postgresql: { external: { sslmode: "require" } } }],
			/sslmode/u,
		],
		[
			"an external database in plain text",
			[...layersFor("cluster"), { postgresql: { external: { sslmode: "disable" } } }],
			/sslmode/u,
		],
		[
			"a worker without the API endpoint",
			[...layersFor("small"), { worker: { enabled: true, runtime: workerRuntime } }],
			/networkPolicy\.kubernetesApi\.cidr is required/u,
		],
		[
			"a worker without the API port",
			worker({ networkPolicy: { kubernetesApi: { port: 0 } } }),
			/networkPolicy\.kubernetesApi\.port is required/u,
		],
		[
			"a worker without a process limit",
			worker({ worker: { runtime: { podPidsLimit: 0 } } }),
			/worker\.runtime\.podPidsLimit is required/u,
		],
		[
			"a process limit above the runtime's bound",
			worker({ worker: { runtime: { podPidsLimit: 4097 } } }),
			/podPidsLimit/u,
		],
		[
			"a seccomp profile outside the kubelet's directory",
			worker({ worker: { runtime: { seccompProfile: "../agent.json" } } }),
			/worker\.runtime\.seccompProfile must stay inside/u,
		],
	];
	for (const [label, layers, message] of cases) {
		const result = render(layers);
		assert.equal(result.ok, false, `${label} rendered`);
		assert.match(result.stderr, message, label);
	}
});
