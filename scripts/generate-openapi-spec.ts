import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const serverDirectory = join(import.meta.dirname, "..", "server");
const specification = join(serverDirectory, "openapi.yaml");
const previous = existsSync(specification) ? readFileSync(specification) : undefined;

rmSync(specification, { force: true });

const wrapper = process.platform === "win32" ? "mvnw.cmd" : "./mvnw";
const dockerAvailable = spawnSync("docker", ["info"], { stdio: "ignore" }).status === 0;
const result = spawnSync(
	wrapper,
	["verify", "-DskipTests=true", "-Dapp.profiles=specs", ...process.argv.slice(2)],
	{
		cwd: serverDirectory,
		stdio: "inherit",
		env: dockerAvailable ? process.env : { ...process.env, SPRING_DOCKER_COMPOSE_ENABLED: "false" },
		shell: process.platform === "win32",
	},
);

const generated = existsSync(specification) && readFileSync(specification).length > 0;
if (result.status === 0 && generated) process.exit(0);

if (previous) writeFileSync(specification, previous);
else rmSync(specification, { force: true });

if (result.error) console.error(result.error.message);
if (!generated) console.error("OpenAPI generation did not produce server/openapi.yaml.");
process.exit(result.status && result.status > 0 ? result.status : 1);
