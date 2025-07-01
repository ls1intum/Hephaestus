import { Welcome } from "@/components/mentor/Welcome";
import { ChatInput } from "@/components/mentor/ChatInput";
import { v4 as uuidv4 } from "uuid";

import {
	getGroupedThreadsQueryKey,
	getThreadQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { ChatThreadDetail, ChatThreadGroup } from "@/api/types.gen";
import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/mentor/")({
	component: MentorContainer,
});

function MentorContainer() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();

	const handleSendMessage = (text: string) => {
		const threadId = uuidv4();

		// Optimistically update the thread detail cache
		queryClient.setQueryData(getThreadQueryKey({ path: { threadId } }), {
			id: threadId,
			messages: [],
		} satisfies ChatThreadDetail);

		// Optimistically update the grouped threads cache
		const previousGroupedThreads: Array<ChatThreadGroup> | undefined =
			queryClient.getQueryData(getGroupedThreadsQueryKey());
		let nextGroupThreads: Array<ChatThreadGroup> = [];
		const threadSummary = {
			id: threadId,
			title: "New chat",
			createdAt: new Date(),
		};
		if (previousGroupedThreads) {
			if (previousGroupedThreads[0]?.groupName === "Today") {
				nextGroupThreads = [
					{
						groupName: "Today",
						threads: [threadSummary, ...previousGroupedThreads[0].threads],
					},
					...previousGroupedThreads.slice(1),
				];
			} else {
				nextGroupThreads = [
					{
						groupName: "Today",
						threads: [threadSummary],
					},
					...previousGroupedThreads,
				];
			}
		} else {
			nextGroupThreads = [
				{
					groupName: "Today",
					threads: [threadSummary],
				},
			];
		}
		queryClient.setQueryData(getGroupedThreadsQueryKey(), nextGroupThreads);

		navigate({
			to: "/mentor/$threadId",
			params: { threadId },
			state: { pendingMentorMessage: text },
		});
	};

	return (
		<div className="h-[calc(100vh-4rem)] max-w-5xl mx-auto p-6 flex flex-col">
			<div className="flex-1 flex items-center justify-center">
				<Welcome />
			</div>
			<div className="mt-6">
				<ChatInput
					onSubmit={handleSendMessage}
					placeholder="Ask me anything about software development, best practices, or technical concepts..."
					autoFocus
				/>
			</div>
		</div>
	);
}
