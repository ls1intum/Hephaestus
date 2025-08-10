import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useCallback } from "react";
import { v4 as uuidv4 } from "uuid";

import {
	getGroupedThreadsQueryKey,
	getThreadQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { ChatThreadGroup, ChatThreadSummary } from "@/api/types.gen";
import type { ChatProps } from "@/components/mentor/Chat";
import { Chat } from "@/components/mentor/Chat";
import type { ChatMessage } from "@/lib/types";

export const Route = createFileRoute("/_authenticated/mentor/")({
	component: MentorContainer,
});

function MentorContainer() {
	const queryClient = useQueryClient();
	const navigate = useNavigate();

	const handleMessageSubmit = useCallback(
		({ text }: { text: string }) => {
			const initialMessage = text.trim();
			if (!initialMessage) return;
			const threadId = uuidv4();
			// Optimistically seed the thread cache so the thread route doesn't show loading
			queryClient.setQueryData(getThreadQueryKey({ path: { threadId } }), {
				messages: [],
			});

			queryClient.setQueryData<Array<ChatThreadGroup>>(
				getGroupedThreadsQueryKey(),
				(prev) => {
					const threadGroups = prev ?? [];
					const newSummary: ChatThreadSummary = {
						id: threadId,
						title: "New chat",
						createdAt: new Date(),
					};
					const idx = threadGroups.findIndex(
						(g) => g.groupName.toLowerCase() === "today",
					);
					if (idx >= 0) {
						const group = threadGroups[idx];
						const exists = group.threads.some((t) => t.id === threadId);
						if (exists) return threadGroups;
						const updatedGroup: ChatThreadGroup = {
							groupName: group.groupName,
							threads: [newSummary, ...group.threads],
						};
						return [
							...threadGroups.slice(0, idx),
							updatedGroup,
							...threadGroups.slice(idx + 1),
						];
					}
					// No Today group yet
					return [
						{ groupName: "Today", threads: [newSummary] },
						...threadGroups,
					];
				},
			);

			// Navigate with initial message state; thread route will send it immediately
			navigate({
				to: "/mentor/$threadId",
				params: { threadId },
				state: { initialMessage },
			});
		},
		[navigate, queryClient],
	);

	// Index route acts as a thin redirector; editing/voting/copy are not used here

	return (
		<div className="flex flex-col flex-1 min-h-0">
			<Chat
				id={""}
				messages={[] as ChatMessage[]}
				status={"idle" as ChatProps["status"]}
				readonly={false}
				attachments={[]}
				onMessageSubmit={handleMessageSubmit}
				onStop={() => {}}
				onFileUpload={() => Promise.resolve([])}
				onAttachmentsChange={() => {}}
				showSuggestedActions={true}
				inputPlaceholder="Ask me anything about software development, best practices, or agile concepts..."
				disableAttachments={true}
				className="h-[calc(100dvh-4rem)]"
			/>
		</div>
	);
}
