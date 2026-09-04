import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readFileSync, existsSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const entrypoint = fileURLToPath(new URL("../webapp/docker/entrypoint.sh", import.meta.url));
const posixOnly = { skip: process.platform === "win32" && "entrypoint.sh is a bash script" };

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

await test("every other host is sent to the origin the SPA is configured for", posixOnly, () => {
	const { serverName, redirect } = configure("https://hephaestus.build");
	assert.equal(serverName, "server_name hephaestus.build localhost 127.0.0.1;\n");
	assert.match(redirect ?? "", /listen 80 default_server;/);
	assert.match(redirect ?? "", /return 301 https:\/\/hephaestus\.build\$request_uri;/);
});

await test("both container healthcheck probes reach the app, not the redirect", posixOnly, () => {
	// nginx prefers an exact server_name over the default server. compose.app.yaml probes
	// `http://localhost` and the preview stack probes `http://127.0.0.1:80/`; a probe that landed on
	// the default server would get a 301, which `curl -f` reports as success without following it —
	// a healthcheck that passes while the SPA is not being served at all.
	const { serverName } = configure("https://hephaestus.build");
	assert.match(serverName, /\blocalhost\b/);
	assert.match(serverName, /\b127\.0\.0\.1\b/);
});

await test(
	"a port in the origin is kept in the redirect but not in the server name",
	posixOnly,
	() => {
		const { serverName, redirect } = configure("https://heph.example:8443");
		assert.equal(serverName, "server_name heph.example localhost 127.0.0.1;\n");
		assert.match(redirect ?? "", /https:\/\/heph\.example:8443\$request_uri/);
	},
);

await test("a deployment with no configured origin answers on every host", posixOnly, () => {
	const { serverName, redirect } = configure(undefined);
	assert.equal(serverName, "server_name localhost 127.0.0.1;\n");
	assert.equal(redirect, undefined, "a stale redirect from an earlier boot must be removed");
});

await test("a value that is not a bare hostname is refused rather than escaped", posixOnly, () => {
	// The value reaches nginx configuration, so injection is refused outright.
	for (const hostile of [
		"https://evil.example;\nserver{listen 80;}",
		"https://host name",
		"https://-leading-dash.example",
		"https://",
		"not-a-url",
		// Everything below hides behind a colon, where a check that stopped at the port would have
		// waved it through: userinfo redirects browsers to the host after the "@", and a newline
		// ends the return directive and starts writing nginx configuration of its own.
		"https://good.example:x@attacker.example",
		"https://good.example:80@attacker.example",
		"https://good.example:80\nserver{listen 80 default_server; return 301 http://attacker.example;}",
		"https://good.example:not-a-port",
		"https://good.example:8443:9000",
	]) {
		const { serverName, redirect } = configure(hostile);
		assert.equal(serverName, "server_name localhost 127.0.0.1;\n", `accepted ${hostile}`);
		assert.equal(redirect, undefined, `wrote a redirect for ${hostile}`);
	}
});
