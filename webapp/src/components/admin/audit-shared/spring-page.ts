/** The Spring `Page` envelope the audit endpoints return. */
export interface SpringPage<T> {
	content?: T[];
	number?: number;
	last?: boolean;
	totalElements?: number;
}

/** Infinite-query paging for a Spring `Page`: advance by page number until the last one. */
export const springPageParams = {
	initialPageParam: 0,
	getNextPageParam: <T>(lastPage: SpringPage<T>) =>
		lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
};
