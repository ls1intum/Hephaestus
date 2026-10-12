import { problemStatusOf } from "@/lib/problem-detail";
import { getUserViewSession } from "@/runtime/user-view/session";

export const QUERY_STALE_TIME_MS = 30_000;

/** TanStack's own retry count, which a query that sets its own `retry` function has to restate. */
export const QUERY_RETRIES = 3;

/**
 * Whether a failed query is retried at all. A user view never retries: it starts and ends with a page
 * load, and every retried view read is checked and audited again. A query that sets its own `retry`
 * replaces the client's default outright, so it asks this too.
 */
export function sessionRetriesQueries(): boolean {
	return getUserViewSession() === undefined;
}

/** A 404 is a record that does not exist here: asking again answers the same, only later. */
export function retryUnlessNotFound(failureCount: number, error: unknown): boolean {
	return sessionRetriesQueries() && problemStatusOf(error) !== 404 && failureCount < QUERY_RETRIES;
}
