import { type MutationKey, useMutationState } from "@tanstack/react-query";
import { isRecord } from "@/lib/is-record";

export function filedUnder<TOptions extends object>(
	mutationKey: MutationKey,
	options: TOptions,
): TOptions & { mutationKey: MutationKey } {
	return { ...options, mutationKey };
}

/**
 * Reads one path parameter out of a mutation's variables.
 *
 * The mutation cache holds every mutation the app has fired, so it types `variables` as `unknown` —
 * a filter by key narrows which mutations come back but not what they carry. Checking the value is
 * what keeps that honest: a mutation whose variables do not have the field contributes no id, rather
 * than a claim that would surface as `undefined` somewhere further away.
 */
function pathParam(variables: unknown, field: string): unknown {
	if (!isRecord(variables) || !isRecord(variables.path)) return undefined;
	return variables.path[field];
}

export function pathNumber(variables: unknown, field: string): number | undefined {
	const value = pathParam(variables, field);
	return typeof value === "number" ? value : undefined;
}

export function pathString(variables: unknown, field: string): string | undefined {
	const value = pathParam(variables, field);
	return typeof value === "string" ? value : undefined;
}

/**
 * The ids of the mutations currently in flight under `mutationKey`, for disabling the rows they are
 * about. `idOf` reads the id from a mutation's variables — see {@link pathNumber}/{@link pathString}.
 */
export function usePendingMutationIds<TId extends string | number>(
	mutationKey: MutationKey,
	idOf: (variables: unknown) => TId | undefined,
): ReadonlySet<TId> {
	const ids = useMutationState({
		filters: { mutationKey, status: "pending" },
		select: (mutation) => idOf(mutation.state.variables),
	});
	return new Set(ids.filter((id): id is TId => id != null));
}
