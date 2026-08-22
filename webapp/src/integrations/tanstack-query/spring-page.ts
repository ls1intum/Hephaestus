import type { InfiniteData } from "@tanstack/react-query";

export interface SpringPage<T> {
	content?: T[];
	number?: number;
	last?: boolean;
	totalElements?: number;
}

export const springPageParams = {
	initialPageParam: 0,
	getNextPageParam: <T>(lastPage: SpringPage<T>) =>
		lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
};

/**
 * The generated client assembles its infinite query options behind a `@ts-ignore`, and what comes
 * out resolves `useInfiniteQuery` to the overload that declares `data` always defined. It is not:
 * every infinite query renders at least once with nothing loaded.
 */
export function loadedPages<TPage>(data: InfiniteData<TPage> | undefined): TPage[] {
	return data?.pages ?? [];
}
