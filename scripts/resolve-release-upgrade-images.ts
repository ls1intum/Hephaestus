import { spawnSync } from "node:child_process";
import { appendFileSync } from "node:fs";
import process from "node:process";

const applicationRepository = "ghcr.io/ls1intum/hephaestus/application-server";
const postgresRepository = "ghcr.io/ls1intum/hephaestus/postgres";

function command(executable: string, args: string[]): string {
	const result = spawnSync(executable, args, { encoding: "utf8" });
	if (result.status !== 0)
		throw new Error(`${executable} ${args.join(" ")} failed:\n${result.stdout}${result.stderr}`);
	return result.stdout.trim();
}

function nonEmpty(value: string | undefined): string | undefined {
	return value === undefined || value === "" ? undefined : value;
}

function immutable(reference: string, repository: string): string {
	if (!reference.startsWith(`${repository}:`) && !reference.startsWith(`${repository}@sha256:`))
		throw new Error(`Unexpected image repository: ${reference}`);
	const manifest = command("docker", [
		"buildx",
		"imagetools",
		"inspect",
		reference,
		"--format",
		"{{json .Manifest}}",
	]);
	const parsed: unknown = JSON.parse(manifest);
	if (
		typeof parsed !== "object" ||
		parsed === null ||
		!("digest" in parsed) ||
		typeof parsed.digest !== "string" ||
		!/^sha256:[a-f0-9]{64}$/.test(parsed.digest)
	)
		throw new Error(`Registry returned an invalid digest for ${reference}`);
	return `${repository}@${parsed.digest}`;
}

const supplied = [
	process.env.INPUT_PREVIOUS_APP,
	process.env.INPUT_CANDIDATE_APP,
	process.env.INPUT_POSTGRES,
];
if (supplied.some(Boolean) && !supplied.every(Boolean))
	throw new Error("Reusable workflow callers must provide all three image references");

let [previousApplication, candidateApplication, postgres] = supplied;
if (!previousApplication || !candidateApplication || !postgres) {
	const repository = process.env.GITHUB_REPOSITORY;
	if (!repository) throw new Error("GITHUB_REPOSITORY is required");
	const requestedPrevious = nonEmpty(process.env.REQUESTED_PREVIOUS);
	const previous =
		requestedPrevious ??
		command("gh", [
			"release",
			"view",
			"--repo",
			repository,
			"--json",
			"tagName",
			"--jq",
			".tagName",
		]);
	if (!/^v[0-9]+\.[0-9]+\.[0-9]+$/.test(previous))
		throw new Error("Previous release must be a stable vX.Y.Z tag");
	const candidate = nonEmpty(process.env.REQUESTED_CANDIDATE) ?? process.env.GITHUB_SHA;
	if (!candidate || !/^[a-f0-9]{40}$/.test(candidate))
		throw new Error("Candidate must be a full commit SHA");
	previousApplication = `${applicationRepository}:${previous.slice(1)}`;
	candidateApplication = `${applicationRepository}:${candidate}`;
	postgres = `${postgresRepository}:${candidate}`;
}

const output = process.env.GITHUB_OUTPUT;
if (!output) throw new Error("GITHUB_OUTPUT is required");
appendFileSync(
	output,
	[
		`previous-app=${immutable(previousApplication, applicationRepository)}`,
		`candidate-app=${immutable(candidateApplication, applicationRepository)}`,
		`postgres=${immutable(postgres, postgresRepository)}`,
		"",
	].join("\n"),
);
