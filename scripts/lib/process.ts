import { execFile, spawn } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

export interface RunOptions {
	cwd?: string;
	env?: Record<string, string | undefined>;
	stdin?: "inherit" | "ignore";
	stdout?: "inherit" | "ignore";
	stderr?: "inherit" | "ignore";
	signal?: AbortSignal;
}

export async function run(
	command: string,
	args: string[],
	options: RunOptions = {},
): Promise<void> {
	const child = spawn(command, args, {
		cwd: options.cwd,
		env: { ...process.env, ...options.env },
		stdio: [options.stdin ?? "inherit", options.stdout ?? "inherit", options.stderr ?? "inherit"],
		signal: options.signal,
	});
	await new Promise<void>((resolve, reject) => {
		child.once("error", reject);
		child.once("exit", (code, signal) =>
			code === 0
				? resolve()
				: reject(new Error(`${command} exited with ${signal ?? `code ${code}`}`)),
		);
	});
}

export async function succeeds(
	command: string,
	args: string[],
	options: RunOptions = {},
): Promise<boolean> {
	try {
		await run(command, args, { ...options, stdout: "ignore", stderr: "ignore" });
		return true;
	} catch {
		return false;
	}
}

export async function output(
	command: string,
	args: string[],
	options: RunOptions = {},
): Promise<string> {
	const { stdout } = await execFileAsync(command, args, {
		cwd: options.cwd,
		env: { ...process.env, ...options.env },
		signal: options.signal,
		encoding: "utf8",
	});
	return stdout;
}
