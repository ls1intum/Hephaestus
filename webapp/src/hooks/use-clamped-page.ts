import { useEffect } from "react";

/**
 * Walks a reader back to the last page that exists once the result count says the one they asked for
 * does not.
 *
 * A page number lives in the URL, so it outlives the result set that made it reachable: a bookmark on
 * page nine, a filter narrowed to two pages, and the ninth page of two answers with nothing — an
 * empty list with no filter to clear and no way back except editing the address bar. Neither the
 * parser nor the render can prevent it, because the total only arrives with the response.
 *
 * <p>It corrects by navigating rather than by rendering a different page than the URL names, so the
 * address stays shareable and the correction survives a reload.
 */
export function useClampedPage(
	page: number | undefined,
	totalPages: number | undefined,
	onPageChange: (page: number) => void,
) {
	useEffect(() => {
		if (totalPages !== undefined && page && page >= totalPages) {
			onPageChange(Math.max(0, totalPages - 1));
		}
	}, [onPageChange, page, totalPages]);
}
