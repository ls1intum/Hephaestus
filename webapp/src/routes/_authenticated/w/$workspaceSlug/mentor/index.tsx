import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef } from "react";
import { v4 as uuidv4 } from "uuid";

import { getGroupedThreadsQueryKey, getThreadQueryKey } from "@/api/@tanstack/react-query.gen";
import type { ChatThreadGroup, ChatThreadSummary } from "@/api/types.gen";
import { Greeting } from "@/components/mentor/Greeting";
import { useWorkspace } from "@/hooks/use-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/mentor/")({
	component: MentorContainer,
});

function MentorContainer() {
	const queryClient = useQueryClient();
	const navigate = useNavigate({ from: Route.fullPath });
	// Workspace is loaded by the parent layout route and provided via context
	const { workspaceSlug } = useWorkspace();
	const hasStartedRef = useRef(false);

	// Auto-start a new conversation when the page loads
	useEffect(() => {
		if (hasStartedRef.current) return;
		hasStartedRef.current = true;

		const threadId = uuidv4();

		// Pre-populate thread cache
		queryClient.setQueryData(getThreadQueryKey({ path: { workspaceSlug, threadId } }), {
			messages: [],
		});

		// Add to thread list
		queryClient.setQueryData<Array<ChatThreadGroup>>(
			getGroupedThreadsQueryKey({ path: { workspaceSlug } }),
			(prev) => {
				const threadGroups = prev ?? [];
				const newSummary: ChatThreadSummary = {
					id: threadId,
					title: "New chat",
					createdAt: new Date(),
				};
				const idx = threadGroups.findIndex((g) => g.groupName.toLowerCase() === "today");
				if (idx >= 0) {
					const group = threadGroups[idx];
					const exists = group.threads.some((t) => t.id === threadId);
					if (exists) return threadGroups;
					const updatedGroup: ChatThreadGroup = {
						groupName: group.groupName,
						threads: [newSummary, ...group.threads],
					};
					return [...threadGroups.slice(0, idx), updatedGroup, ...threadGroups.slice(idx + 1)];
				}
				return [{ groupName: "Today", threads: [newSummary] }, ...threadGroups];
			},
		);

		// Navigate to thread page - it will trigger the greeting via autoGreeting
		navigate({
			to: "/w/$workspaceSlug/mentor/$threadId",
			params: { workspaceSlug, threadId },
			state: { autoGreeting: true },
			replace: true,
		});
	}, [workspaceSlug, queryClient, navigate]);

	// Show greeting animation while redirecting
	return (
		<div className="flex flex-col flex-1 min-h-0 h-[calc(100dvh-4rem)]">
			<Greeting />
		</div>
	);
}
