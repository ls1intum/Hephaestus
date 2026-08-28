#!/usr/bin/env node
import { readFileSync } from "node:fs";

import { asRecord, isRecord, parseJson } from "./lib/json.ts";

const dockerfile = readFileSync("docker/agents/pi/Dockerfile", "utf8");
const packageJson = asRecord(parseJson(readFileSync("package.json", "utf8")), "package.json");
const devDependencies = isRecord(packageJson.devDependencies) ? packageJson.devDependencies : {};
const problems: string[] = [];

function dockerArg(name: string): string {
	const match = new RegExp(`^ARG ${name}=(\\S+)$`, "m").exec(dockerfile);
	if (!match?.[1]) {
		problems.push(`docker/agents/pi/Dockerfile: missing ARG ${name}.`);
		return "";
	}
	return match[1];
}

const bunVersion = dockerArg("BUN_VERSION");
const piVersion = dockerArg("PI_VERSION");
const packagePiVersion = devDependencies["@earendil-works/pi-coding-agent"];
if (packagePiVersion !== piVersion) {
	problems.push(
		`package.json pins Pi ${String(packagePiVersion)} but the agent image pins ${piVersion}.`,
	);
}

const javaPin = /private static final String PI_SDK_VERSION = "([^"]+)";/;
for (const path of [
	"server/application/src/test/java/de/tum/cit/aet/hephaestus/agent/mentor/live/MentorLiveLlmTest.java",
	"server/application/src/test/java/de/tum/cit/aet/hephaestus/agent/mentor/live/MentorSandboxStressTest.java",
	"server/application/src/test/java/de/tum/cit/aet/hephaestus/agent/practice/live/PracticeRunnerLiveLlmTest.java",
]) {
	const testVersion = javaPin.exec(readFileSync(path, "utf8"))?.[1];
	if (testVersion !== piVersion) {
		problems.push(`${path} pins Pi ${String(testVersion)} but the agent image pins ${piVersion}.`);
	}
}

for (const architecture of ["x64", "aarch64"]) {
	const hash = new RegExp(`bun_arch="${architecture}"; bun_sha256="([a-f0-9]+)"`).exec(
		dockerfile,
	)?.[1];
	if (!hash || hash.length !== 64) {
		problems.push(`docker/agents/pi/Dockerfile: Bun ${architecture} needs a 64-character SHA-256.`);
	}
}

for (const marker of [
	"datasource=github-releases depName=oven-sh/bun",
	"datasource=npm depName=@earendil-works/pi-coding-agent",
]) {
	if (!dockerfile.includes(`# renovate: ${marker}`)) {
		problems.push(`docker/agents/pi/Dockerfile: missing Renovate marker '${marker}'.`);
	}
}

if (problems.length > 0) {
	for (const problem of problems) console.error(`error: ${problem}`);
	process.exit(1);
}

console.log(
	`Agent runtime pins agree: Bun ${bunVersion} (x64 and arm64 hashes present); Pi ${piVersion} (image, TypeScript, and live tests).`,
);
