// Writes server/openapi.yaml from the executable JAR: boots it under the `specs` profile, fetches
// springdoc's YAML verbatim, and stops it. HEPHAESTUS_APPLICATION_JAR names an already-built JAR
// (CI passes the reactor artifact); otherwise the reactor is packaged first, so the documented API
// always comes from the same executable the other gates run.
import { spawn } from "node:child_process";
import { once } from "node:events";
import { glob, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:net";
import { join } from "node:path";
import process from "node:process";
import { setTimeout as sleep } from "node:timers/promises";

import { run } from "./lib/process.ts";

const serverDirectory = join(import.meta.dirname, "..", "server");
const specification = join(serverDirectory, "openapi.yaml");
const wrapper = process.platform === "win32" ? "mvnw.cmd" : "./mvnw";
const startupBudgetMs = 180_000;

async function executableJar(): Promise<string> {
	const configured = process.env.HEPHAESTUS_APPLICATION_JAR;
	if (configured) return configured;
	await run(
		wrapper,
		[
			"-pl",
			"application",
			"-am",
			"package",
			"-Dmaven.test.skip=true",
			"--batch-mode",
			...process.argv.slice(2),
		],
		{ cwd: serverDirectory },
	);
	const jars = (
		await Array.fromAsync(
			glob("application/target/hephaestus-application-*.jar", { cwd: serverDirectory }),
		)
	).filter((jar) => !/-(?:sources|javadoc)\.jar$/.test(jar));
	if (jars.length !== 1) throw new Error(`Expected one executable JAR, found ${jars.length}`);
	return join(serverDirectory, jars[0] ?? "");
}

async function freePort(): Promise<number> {
	const probe = createServer().listen(0, "127.0.0.1");
	await once(probe, "listening");
	const address = probe.address();
	probe.close();
	await once(probe, "close");
	if (!address || typeof address === "string") throw new Error("Could not allocate a port");
	return address.port;
}

async function fetchSpecification(url: string, child: ReturnType<typeof spawn>): Promise<string> {
	const deadline = Date.now() + startupBudgetMs;
	while (Date.now() < deadline) {
		if (child.exitCode !== null) throw new Error(`The server exited with code ${child.exitCode}`);
		try {
			const response = await fetch(url, { signal: AbortSignal.timeout(deadline - Date.now()) });
			if (response.ok) return await response.text();
		} catch {
			// Not listening yet.
		}
		await sleep(1000);
	}
	throw new Error(`The server did not serve ${url} within ${startupBudgetMs / 1000}s`);
}

const jar = await executableJar();
const port = await freePort();
const child = spawn(
	"java",
	[
		"-jar",
		jar,
		"--spring.profiles.active=specs",
		`--server.port=${port}`,
		"--management.server.port=0",
	],
	{ cwd: serverDirectory, stdio: ["ignore", "inherit", "inherit"] },
);
const exited = once(child, "exit");
try {
	const yaml = await fetchSpecification(`http://127.0.0.1:${port}/v3/api-docs.yaml`, child);
	if (!yaml.trim()) throw new Error("The server returned an empty specification");
	await rm(specification, { force: true });
	await writeFile(specification, yaml);
	console.log(`Wrote ${specification} (${(await readFile(specification)).length} bytes)`);
} finally {
	child.kill("SIGTERM");
	await Promise.race([exited, sleep(15_000).then(() => child.kill("SIGKILL"))]);
}
