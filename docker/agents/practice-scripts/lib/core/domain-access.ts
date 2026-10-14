import type { JobFiles } from "./ipc.ts";
import type { ProgramPort } from "./program-context.ts";

/** Host read capabilities stay outside the authored input record. */
const access = new WeakMap<object, { files: JobFiles | null; port: ProgramPort }>();

export function registerDomainAccess(
	context: object,
	files: JobFiles | null,
	port: ProgramPort,
): void {
	access.set(context, { files, port });
}

export function domainAccess(context: object) {
	const granted = access.get(context);
	if (granted === undefined) {
		throw new Error("the host did not grant domain reads");
	}
	return granted;
}
