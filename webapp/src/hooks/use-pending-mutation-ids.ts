import { type MutationKey, useMutationState } from "@tanstack/react-query";

/**
 * Files a generated mutation's options under `mutationKey` so {@link usePendingMutationIds} can find
 * its calls. Spread order matters and is structural here: written inline as
 * `{ mutationKey: KEY, ...generatedMutation() }` it would silently break the day the generator
 * starts emitting a `mutationKey` of its own, with no test to catch it.
 */
export function filedUnder<TOptions extends object>(
	mutationKey: MutationKey,
	options: TOptions,
): TOptions & { mutationKey: MutationKey } {
	return { ...options, mutationKey };
}

/**
 * The ids of every mutation in flight under `mutationKey` (matched by prefix), read off each call's
 * own variables. A single `useState("which row is busy")` cannot describe two rows at once: the
 * first to settle clears the flag for both, and a row whose request is still out looks idle and can
 * be fired again.
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
