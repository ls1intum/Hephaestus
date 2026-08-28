export async function readEnvFile(path: string): Promise<Record<string, string>> {
	const file = Bun.file(path);
	if (!(await file.exists())) return {};
	const values: Record<string, string> = {};
	for (const line of (await file.text()).split(/\r?\n/)) {
		const match = /^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line);
		if (!match) continue;
		const [, key, rawValue] = match;
		if (!key || rawValue === undefined) continue;
		const value = rawValue.trim();
		values[key] =
			(value.startsWith('"') && value.endsWith('"')) ||
			(value.startsWith("'") && value.endsWith("'"))
				? value.slice(1, -1)
				: value;
	}
	return values;
}

export function positivePort(value: string, name: string): number {
	if (!/^\d+$/.test(value)) throw new Error(`${name} must be an integer from 1 to 65535`);
	const port = Number(value);
	if (port < 1 || port > 65_535) throw new Error(`${name} must be an integer from 1 to 65535`);
	return port;
}

/** A required environment variable, or a failure naming it. */
export function requiredEnv(environment: NodeJS.ProcessEnv, name: string): string {
	const value = environment[name];
	if (!value) throw new Error(`${name} is not configured.`);
	return value;
}

/** A required environment variable that must be a positive whole number. */
export function requiredPositiveInteger(environment: NodeJS.ProcessEnv, name: string): number {
	const value = Number(requiredEnv(environment, name));
	if (!Number.isSafeInteger(value) || value <= 0) {
		throw new Error(`${name} must be a positive whole number.`);
	}
	return value;
}
