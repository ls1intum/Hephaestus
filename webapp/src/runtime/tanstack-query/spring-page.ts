import type { InfiniteData } from "@tanstack/react-query";

/**
 * A flat Spring page (`PageResponseDto*`): one page of rows, with its place and the total at the root.
 * Every field is required, so a response of another shape fails the type check instead of reading as
 * a single page with no total.
 */
export interface SpringPage<T> {
	content: T[];
	number: number;
	last: boolean;
	totalElements: number;
}

export const springPageParams = {
	initialPageParam: 0,
	getNextPageParam: <T>(lastPage: SpringPage<T>) =>
		lastPage.last ? undefined : lastPage.number + 1,
};

/** A server slice: one page and whether another follows, with no total to count. */
export interface SlicePage {
	page?: number;
	hasNext?: boolean;
}

export const slicePageParams = {
	initialPageParam: 0,
	getNextPageParam: (lastPage: SlicePage) =>
		lastPage.hasNext === true ? (lastPage.page ?? 0) + 1 : undefined,
};

/**
 * The generated client assembles its infinite query options behind a `@ts-ignore`, and what comes out
 * picks the `useInfiniteQuery` overload that declares `data` always defined. It is not — every
 * infinite query renders at least once with nothing loaded — so take pages through here, not `.pages`.
 */
export function loadedPages<TPage>(data: InfiniteData<TPage> | undefined): TPage[] {
	return data?.pages ?? [];
}
