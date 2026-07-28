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
