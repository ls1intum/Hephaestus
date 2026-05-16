import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef } from "react";
import { v4 as uuidv4 } from "uuid";

import { getThreadQueryKey, listThreadsQueryKey } from "@/api/@tanstack/react-query.gen";
import type { ChatThreadSummary } from "@/api/types.gen";
import { Greeting } from "@/components/mentor/Greeting";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/mentor/")({
	component: MentorContainer,
});

function MentorContainer() {
	const queryClient = useQueryClient();
	const navigate = useNavigate({ from: Route.fullPath });
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const hasStartedRef = useRef(false);

	// Auto-start a new conversation when the page loads
	useEffect(() => {
		if (!workspaceSlug || hasStartedRef.current) return;
		hasStartedRef.current = true;

		const threadId = uuidv4();

		// Pre-populate thread cache
		queryClient.setQueryData(getThreadQueryKey({ path: { workspaceSlug: slug, threadId } }), {
			messages: [],
		});

		// Add to thread list (flat list; NavMentorThreads buckets by createdAt)
		queryClient.setQueryData<Array<ChatThreadSummary>>(
			listThreadsQueryKey({ path: { workspaceSlug: slug } }),
			(prev) => {
				const threads = prev ?? [];
				if (threads.some((t) => t.id === threadId)) return threads;
				const newSummary: ChatThreadSummary = {
					id: threadId,
					title: "New chat",
					createdAt: new Date(),
				};
				return [newSummary, ...threads];
			},
		);

		// Navigate to thread page — the static <Greeting /> there renders on empty messages,
		// then the user's first message starts the real chat turn.
		navigate({
			to: "/w/$workspaceSlug/mentor/$threadId",
			params: { workspaceSlug: slug, threadId },
			replace: true,
		});
	}, [workspaceSlug, slug, queryClient, navigate]);

	if (!workspaceSlug) {
		return <NoWorkspace />;
	}

	// Show greeting animation while redirecting
	return (
		<div className="flex flex-col flex-1 min-h-0 h-[calc(100dvh-4rem)]">
			<Greeting />
		</div>
	);
}
