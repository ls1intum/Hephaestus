import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readFileSync, existsSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const entrypoint = fileURLToPath(new URL("../webapp/docker/entrypoint.sh", import.meta.url));

/** Runs the image entrypoint's canonical-host step against a throwaway nginx config directory. */
function configure(clientUrl: string | undefined): {
	serverName: string;
	redirect: string | undefined;
} {
	const nginxDir = mkdtempSync(join(tmpdir(), "nginx-"));
	mkdirSync(join(nginxDir, "conf.d"));
	// A stale file from an earlier boot of the same container must not survive a config change.
	writeFileSync(join(nginxDir, "conf.d", "canonical-redirect.conf"), "# stale\n");

	execFileSync("bash", ["-c", `source "${entrypoint}"; configure_canonical_host`], {
		env: {
			PATH: process.env.PATH ?? "",
			NGINX_DIR: nginxDir,
			...(clientUrl === undefined ? {} : { APPLICATION_CLIENT_URL: clientUrl }),
		},
		stdio: ["ignore", "ignore", "ignore"],
	});

	const redirectPath = join(nginxDir, "conf.d", "canonical-redirect.conf");
	return {
		serverName: readFileSync(join(nginxDir, "canonical-server-name.conf"), "utf8"),
		redirect: existsSync(redirectPath) ? readFileSync(redirectPath, "utf8") : undefined,
	};
}

await test("every other host is sent to the origin the SPA is configured for", () => {
	const { serverName, redirect } = configure("https://hephaestus.build");
	assert.equal(serverName, "server_name hephaestus.build localhost;\n");
	assert.match(redirect ?? "", /listen 80 default_server;/);
	assert.match(redirect ?? "", /return 301 https:\/\/hephaestus\.build\$request_uri;/);
});

await test("the healthcheck keeps reaching the app, not the redirect", () => {
	// nginx prefers an exact server_name over the default server, so naming localhost here is
	// what stops `curl -f http://localhost` from being answered with a 301.
	assert.match(configure("https://hephaestus.build").serverName, /\blocalhost;/);
});

await test("a port in the origin is kept in the redirect but not in the server name", () => {
	const { serverName, redirect } = configure("https://heph.example:8443");
	assert.equal(serverName, "server_name heph.example localhost;\n");
	assert.match(redirect ?? "", /https:\/\/heph\.example:8443\$request_uri/);
});

await test("a deployment with no configured origin answers on every host", () => {
	const { serverName, redirect } = configure(undefined);
	assert.equal(serverName, "server_name localhost;\n");
	assert.equal(redirect, undefined, "a stale redirect from an earlier boot must be removed");
});

await test("a value that is not a bare hostname is refused rather than escaped", () => {
	// The value reaches nginx configuration, so injection is refused outright.
	for (const hostile of [
		"https://evil.example;\nserver{listen 80;}",
		"https://host name",
		"https://-leading-dash.example",
		"https://",
		"not-a-url",
	]) {
		const { serverName, redirect } = configure(hostile);
		assert.equal(serverName, "server_name localhost;\n", `accepted ${hostile}`);
		assert.equal(redirect, undefined, `wrote a redirect for ${hostile}`);
	}
});
