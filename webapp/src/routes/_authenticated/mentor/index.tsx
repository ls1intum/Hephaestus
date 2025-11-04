import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { v4 as uuidv4 } from "uuid";

import { getMentorThreadsByThreadIdQueryKey } from "@/api/@tanstack/react-query.gen";
import type { ChatProps } from "@/components/mentor/Chat";
import { Chat } from "@/components/mentor/Chat";
import { defaultPartRenderers } from "@/components/mentor/renderers";
import type { ChatMessage } from "@/lib/types";

export const Route = createFileRoute("/_authenticated/mentor/")({
	component: MentorContainer,
});

function MentorContainer() {
	const queryClient = useQueryClient();
	const navigate = useNavigate();

	const handleMessageSubmit = ({ text }: { text: string }) => {
		const initialMessage = text.trim();
		if (!initialMessage) return;
		const threadId = uuidv4();
		// Optimistically seed the thread cache so the thread route doesn't show loading
		queryClient.setQueryData(
			getMentorThreadsByThreadIdQueryKey({ path: { threadId } }),
			{
				messages: [],
			},
		);

		// Navigate with initial message state; thread route will send it immediately
		navigate({
			to: "/mentor/$threadId",
			params: { threadId },
			state: { initialMessage },
		});
	};

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
				partRenderers={defaultPartRenderers}
			/>
		</div>
	);
}
