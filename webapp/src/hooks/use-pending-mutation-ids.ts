import { type MutationKey, useMutationState } from "@tanstack/react-query";

export function filedUnder<TOptions extends object>(
	mutationKey: MutationKey,
	options: TOptions,
): TOptions & { mutationKey: MutationKey } {
	return { ...options, mutationKey };
}

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
