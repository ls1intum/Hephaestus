import { describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { duplicatePorts } from "./check-ports.ts";
import { withDisposableDatabase } from "./db-utils.ts";
import { loadConfig } from "./e2e-setup.ts";
import { isHostname } from "./jean-public-test.ts";
import { updateEnv } from "./jean-setup.ts";
import { positivePort, readEnvFile } from "./lib/env.ts";
import { parseIterations } from "./qualify-bun-lockfile.ts";

describe("environment parsing", () => {
	test("parses data without evaluating shell syntax", async () => {
		const directory = await mkdtemp(join(tmpdir(), "hephaestus-env-"));
		try {
			const path = join(directory, ".env");
			await writeFile(path, "PORT=5432\nSECRET=$(echo leaked)\nexport BAD=ignored\n");
			expect(await readEnvFile(path)).toEqual({ PORT: "5432", SECRET: "$(echo leaked)" });
		} finally {
			await rm(directory, { recursive: true, force: true });
		}
	});

	test("rejects invalid ports", () => {
		expect(() => positivePort("0", "PORT")).toThrow("1 to 65535");
		expect(() => positivePort("65536", "PORT")).toThrow("1 to 65535");
	});
});

test("duplicate port reporting identifies the conflicting pair", () => {
	const first = { name: "database", port: 5432 };
	const second = { name: "server", port: 5432 };
	expect(duplicatePorts([first, second])).toEqual([[second, first]]);
});

test("duplicate configured ports fail the preflight", async () => {
	const child = Bun.spawn(
		[process.execPath, join(import.meta.dirname, "check-ports.ts"), "--quiet"],
		{
			env: { ...Bun.env, POSTGRES_PORT: "65431", SERVER_PORT: "65431", WEBAPP_PORT: "65431" },
			stdout: "ignore",
			stderr: "ignore",
		},
	);
	expect(await child.exited).toBe(1);
});

test("lockfile qualification accepts only positive integers", () => {
	expect(parseIterations(undefined)).toBe(25);
	expect(parseIterations("3")).toBe(3);
	expect(() => parseIterations("0")).toThrow("positive integer");
	expect(() => parseIterations("1; rm -rf /")).toThrow("positive integer");
	expect(() => parseIterations("999999999999999999999999")).toThrow("safe integer");
});

test("public host validation rejects Traefik rule syntax", () => {
	expect(isHostname("preview.example.com")).toBeTrue();
	expect(isHostname("example.com`) || Host(`attacker.example")).toBeFalse();
});

test("Jean setup updates only GitLab bootstrap settings and remains idempotent", () => {
	const initial =
		"# machine-local config\nGITLAB_PAT=secret\nGITLAB_GROUP_PATH=group\nGITLAB_ENABLED=false\n";
	const updated = updateEnv(initial);
	expect(updated).toStartWith("# machine-local config\n");
	expect(updated).toContain("GITLAB_ENABLED=true");
	expect(updated).toContain("GITLAB_SERVER_URL=https://gitlab.lrz.de");
	expect(updateEnv(updated)).toBe(updated);
});

test("database drafting restores planted state after external-command failure", async () => {
	const directory = await mkdtemp(join(tmpdir(), "hephaestus-db-"));
	const data = join(directory, "postgres-data");
	const backup = join(directory, "backup");
	await mkdir(data);
	await writeFile(join(data, "sentinel"), "original");
	let stops = 0;
	try {
		let failure: unknown;
		try {
			await withDisposableDatabase(
				data,
				backup,
				() => {
					stops += 1;
					return Promise.resolve();
				},
				async () => {
					await mkdir(data);
					await writeFile(join(data, "sentinel"), "disposable");
					throw new Error("planted migration failure");
				},
			);
		} catch (error) {
			failure = error;
		}
		expect(failure).toBeInstanceOf(Error);
		expect(failure instanceof Error ? failure.message : "").toBe("planted migration failure");
		expect(await readFile(join(data, "sentinel"), "utf8")).toBe("original");
		expect(stops).toBe(2);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

test("database drafting does not move data after shutdown failure", async () => {
	const directory = await mkdtemp(join(tmpdir(), "hephaestus-db-stop-"));
	const data = join(directory, "postgres-data");
	const backup = join(directory, "backup");
	await mkdir(data);
	await writeFile(join(data, "sentinel"), "original");
	let stops = 0;
	try {
		let failure: unknown;
		try {
			await withDisposableDatabase(
				data,
				backup,
				() => {
					stops += 1;
					return stops === 1
						? Promise.resolve()
						: Promise.reject(new Error("postgres still running"));
				},
				async () => {
					await mkdir(data);
					await writeFile(join(data, "sentinel"), "disposable");
				},
			);
		} catch (error) {
			failure = error;
		}
		expect(failure instanceof Error ? failure.message : "").toBe("postgres still running");
		expect(await readFile(join(backup, "sentinel"), "utf8")).toBe("original");
		expect(await readFile(join(data, "sentinel"), "utf8")).toBe("disposable");
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

test("scripts contains no shell implementation", async () => {
	const entries = await readdir(import.meta.dirname, { recursive: true });
	expect(entries.filter((path) => path.endsWith(".sh"))).toEqual([]);
});

describe("E2E setup trust boundaries", () => {
	const valid = {
		E2E_GITLAB_PAT: "pat-secret",
		E2E_LLM_KEY: "llm-secret",
		E2E_LLM_BASE_URL: "https://models.example/v1",
		E2E_MODEL: "model",
		E2E_LLM_PRICING_MODE: "NO_CHARGE",
		E2E_LLM_PRICE_NOTE: "internal test credits",
	};

	test("accepts credentials only from the environment", () => {
		const config = loadConfig(valid, []);
		expect(config.pat).toBe("pat-secret");
		expect(config.llmKey).toBe("llm-secret");
		expect(() => loadConfig(valid, ["--llm-key", "leak"])).toThrow("invalid argument");
	});

	test("rejects non-loopback app and database endpoints", () => {
		expect(() => loadConfig({ ...valid, E2E_APP_URL: "https://public.example" }, [])).toThrow(
			"loopback",
		);
		expect(() =>
			loadConfig({ ...valid, E2E_DB_URL: "postgresql://secret@db.example/app" }, []),
		).toThrow("loopback");
	});

	test("diagnostics never contain credential values", () => {
		for (const env of [
			{ ...valid, E2E_LLM_PRICING_MODE: "invalid" },
			{ ...valid, E2E_REPO: "bad;repo" },
		]) {
			let diagnostic: string | undefined;
			try {
				loadConfig(env, []);
			} catch (error) {
				diagnostic = error instanceof Error ? error.message : String(error);
			}
			expect(diagnostic).toBeDefined();
			expect(diagnostic).not.toContain("pat-secret");
			expect(diagnostic).not.toContain("llm-secret");
		}
	});
});
