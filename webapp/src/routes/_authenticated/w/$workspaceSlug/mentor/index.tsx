import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { v4 as uuidv4 } from "uuid";

import {
	getGroupedThreadsQueryKey,
	getThreadQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { ChatThreadGroup, ChatThreadSummary } from "@/lib/types";
import type { ChatProps } from "@/components/mentor/Chat";
import { Chat } from "@/components/mentor/Chat";
import { defaultPartRenderers } from "@/components/mentor/renderers";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import type { ChatMessage } from "@/lib/types";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/mentor/",
)({
	component: MentorContainer,
});

function MentorContainer() {
	const queryClient = useQueryClient();
	const navigate = useNavigate({ from: Route.fullPath });
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	if (!workspaceSlug) {
		return <NoWorkspace />;
	}

	const handleMessageSubmit = ({ text }: { text: string }) => {
		const initialMessage = text.trim();
		if (!initialMessage || !workspaceSlug) return;

		const threadId = uuidv4();
		queryClient.setQueryData(
			getThreadQueryKey({ path: { workspaceSlug: slug, threadId } }),
			{ messages: [] },
		);

		queryClient.setQueryData<Array<ChatThreadGroup>>(
			getGroupedThreadsQueryKey({ path: { workspaceSlug: slug } }),
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
				return [{ groupName: "Today", threads: [newSummary] }, ...threadGroups];
			},
		);

		navigate({
			to: "/w/$workspaceSlug/mentor/$threadId",
			params: { workspaceSlug: slug, threadId },
			state: { initialMessage },
		});
	};

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
				partRenderers={defaultPartRenderers}
			/>
		</div>
	);
}
