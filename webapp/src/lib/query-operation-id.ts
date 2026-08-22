import { isRecord } from "@/lib/is-record";

/**
 * The operation a generated query key came from, or `undefined` for a key this client did not
 * build. A cache predicate sees every key as `unknown[]`, including hand-rolled ones, so the tag
 * the generator writes has to be read rather than assumed.
 */
export function queryOperationId(queryKey: readonly unknown[]): string | undefined {
	const [head] = queryKey;
	if (!isRecord(head)) return undefined;
	return typeof head._id === "string" ? head._id : undefined;
}
