import { type QueryKey, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

/** Refresh when polling stops so the list includes the parent's final recorded state. */
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
