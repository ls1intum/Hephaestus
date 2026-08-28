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
	const process = Bun.spawn([command, ...args], {
		cwd: options.cwd,
		env: { ...Bun.env, ...options.env },
		stdin: options.stdin ?? "inherit",
		stdout: options.stdout ?? "inherit",
		stderr: options.stderr ?? "inherit",
		signal: options.signal,
	});
	const exitCode = await process.exited;
	if (exitCode !== 0) throw new Error(`${command} exited with code ${exitCode}`);
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
	const process = Bun.spawn([command, ...args], {
		cwd: options.cwd,
		env: { ...Bun.env, ...options.env },
		stdin: options.stdin ?? "ignore",
		stdout: "pipe",
		stderr: options.stderr ?? "ignore",
		signal: options.signal,
	});
	const [exitCode, stdout] = await Promise.all([
		process.exited,
		new Response(process.stdout).text(),
	]);
	if (exitCode !== 0) throw new Error(`${command} exited with code ${exitCode}`);
	return stdout;
}
