import { type QueryKey, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

/**
 * Reads `queryKeys` once more when `subject` stops being active. A list that polls only while its
 * parent is active would otherwise keep the page it read before the parent's final answer. A
 * subject that was settled when it was first seen, or a switch to another subject, reads nothing.
 */
export function useInvalidateOnSettle(
	subject: string | undefined,
	active: boolean,
	queryKeys: readonly QueryKey[],
) {
	const queryClient = useQueryClient();
	const lastSeen = useRef({ subject, active });
	useEffect(() => {
		const before = lastSeen.current;
		lastSeen.current = { subject, active };
		if (before.subject === subject && before.active && !active) {
			for (const queryKey of queryKeys) {
				void queryClient.invalidateQueries({ queryKey });
			}
		}
	}, [queryClient, subject, active, queryKeys]);
}
