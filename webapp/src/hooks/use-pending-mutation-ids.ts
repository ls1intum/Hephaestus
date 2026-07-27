import { type MutationKey, useMutationState } from "@tanstack/react-query";

/**
 * Files a generated mutation's options under `mutationKey` so {@link usePendingMutationIds} finds its
 * calls. Spread order is structural: inline as `{ mutationKey, ...generated() }` it would break
 * silently the day the generator emits a `mutationKey` of its own.
 */
export function filedUnder<TOptions extends object>(
	mutationKey: MutationKey,
	options: TOptions,
): TOptions & { mutationKey: MutationKey } {
	return { ...options, mutationKey };
}

/**
 * The ids of every mutation in flight under `mutationKey` (prefix-matched), read off each call's own
 * variables. A single `useState` cannot describe two busy rows: the first to settle clears both.
 */
export function usePendingMutationIds<TVariables, TId extends string | number = number>(
	mutationKey: MutationKey,
	idOf: (variables: TVariables) => TId | undefined,
): ReadonlySet<TId> {
	const ids = useMutationState({
		filters: { mutationKey, status: "pending" },
		select: (mutation) => idOf(mutation.state.variables as TVariables),
	});
	return new Set(ids.filter((id): id is TId => id != null));
}
