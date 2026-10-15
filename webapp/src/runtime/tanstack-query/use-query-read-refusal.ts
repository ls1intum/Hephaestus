import { useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useEffect } from "react";

import { problemIsRetryable } from "@/lib/problem-detail";

/** Active resetQueries refetches. Clear refused data without issuing another read or losing the error. */
export function useQueryReadRefusal(queryKey: QueryKey, isError: boolean, error: unknown) {
	const queryClient = useQueryClient();
	useEffect(() => {
		if (!isError || problemIsRetryable(error)) {
			return;
		}
		const cached = queryClient.getQueryCache().find({ queryKey, exact: true });
		if (cached !== undefined && cached.state.error === error && cached.state.data !== undefined) {
			cached.setState({ data: undefined, dataUpdatedAt: 0 });
		}
	}, [queryClient, queryKey, isError, error]);
}
