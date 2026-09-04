import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const STACKS = ["app", "core", "proxy"] as const;

/** Every `traefik.http.routers.<name>.rule=` value in a stack's Compose file. */
function rules(stack: (typeof STACKS)[number]): { router: string; rule: string }[] {
	const file = readFileSync(new URL(`../docker/compose.${stack}.yaml`, import.meta.url), "utf8");
	return [...file.matchAll(/traefik\.http\.routers\.([a-z-]+)\.rule=(.*?)"?$/gm)].map((m) => ({
		router: `${stack}/${m[1]}`,
		rule: m[2],
	}));
}

const all = STACKS.flatMap(rules);

await test("every host-routed router is reachable on the hosts the instance serves", () => {
	assert.ok(all.length >= 8, `expected the stacks to route by host, found ${all.length} rules`);
	for (const { router, rule } of all) {
		assert.match(rule, /APP_HOST_MATCH/, `${router} does not route on the served host set`);
	}
});

await test("a path-scoped router keeps its path condition on every served host", () => {
	// Traefik binds && tighter than ||, so `Host(a) || Host(b) && PathPrefix(/webhooks)` matches
	// host a on *every* path. On the webhook router, which outranks the others, that hands the
	// whole site to the webhook service. Grouping the matcher is what prevents it.
	for (const { router, rule } of all) {
		if (!rule.includes("&&")) continue;
		const hostCondition = rule.slice(0, rule.indexOf("&&")).trim();
		assert.ok(
			hostCondition.startsWith("(") && hostCondition.endsWith(")"),
			`${router} combines the host matcher with a path condition without grouping it: ${hostCondition}`,
		);
	}
});

await test("the documented matcher is one host per Host(), as Traefik v3 requires", () => {
	// Traefik v3 registers Host with expectNParameters(host, 1): Host(`a`,`b`) is rejected outright
	// and the router never loads, so an example in that shape would silently take an instance down.
	for (const path of [
		"../docker/.env.example",
		"../docker/self-host/.env.example",
		"../docs/admin/install.mdx",
	]) {
		const text = readFileSync(new URL(path, import.meta.url), "utf8");
		for (const [example] of text.matchAll(/Host\(`[^`]+`[^)\n]*\)/g)) {
			assert.doesNotMatch(example, /,/, `${path} documents a multi-argument Host(): ${example}`);
		}
	}
});
