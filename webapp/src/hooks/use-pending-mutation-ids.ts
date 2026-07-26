import { type MutationKey, useMutationState } from "@tanstack/react-query";

/**
 * Files a generated mutation's options under `mutationKey`, so {@link usePendingMutationIds} can
 * find its calls.
 *
 * The key has to win over whatever the helper carries, which means applying it *after* the spread.
 * Written inline the other way round — `{ mutationKey: KEY, ...generatedMutation() }` — it is only
 * correct for as long as `@hey-api` keeps emitting `mutationFn` alone: the day the generator also
 * emits a `mutationKey`, every override here is silently dropped, each pending-id lookup returns an
 * empty set, every row re-enables while its request is still out, and no test fails. This makes the
 * precedence structural instead of positional, so regenerating the client cannot quietly undo it.
 */
export function filedUnder<TOptions extends object>(
	mutationKey: MutationKey,
	options: TOptions,
): TOptions & { mutationKey: MutationKey } {
	return { ...options, mutationKey };
}

/**
 * The ids of every mutation currently in flight under `mutationKey`, read off each call's own
 * variables.
 *
 * A single `useState("which row is busy")` flag cannot describe more than one row: start a second
 * row while the first is still running and whichever settles first clears the flag for both, so a
 * control whose request is still in flight goes back to looking idle and can be fired again. The
 * query cache already tracks one entry per call, so ask it instead of shadowing it.
 *
 * `mutationKey` matches by prefix, so several related mutations filed under one prefix — update and
 * delete of the same resource, say — can be asked together with one call.
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
