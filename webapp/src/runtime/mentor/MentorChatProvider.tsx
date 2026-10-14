import { createContext, type ReactNode, use, useState } from "react";

import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { type MentorChatSession, useMentorChatSession } from "@/hooks/use-mentor-chat";
import { hasText } from "@/lib/text";
import { useAuth } from "@/runtime/auth/AuthContext";

const LiveMentorChat = createContext<MentorChatSession | undefined>(undefined);

/**
 * The panel's conversation, held above the routes so that opening the full chat takes it over as
 * it stands, mid-reply included. One per account and workspace: changing either starts a new one.
 */
export function MentorChatProvider({ children }: { children: ReactNode }) {
	const { isAuthenticated, isLoading, userView, getUserId } = useAuth();
	// The account itself: a username is only a login, which two accounts can share across providers.
	const accountId = getUserId();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	// A page that names no workspace keeps the conversation of the last one that did.
	const [lastSlug, setLastSlug] = useState(workspaceSlug);
	if (hasText(workspaceSlug) && workspaceSlug !== lastSlug) {
		setLastSlug(workspaceSlug);
	}
	const active =
		isAuthenticated &&
		!isLoading &&
		userView === undefined &&
		hasText(accountId) &&
		hasText(lastSlug);

	const session = useMentorChatSession({
		workspaceSlug: lastSlug ?? "",
		resetKey: active ? `${accountId}\n${lastSlug}` : undefined,
	});

	return <LiveMentorChat value={active ? session : undefined}>{children}</LiveMentorChat>;
}

/** The panel's conversation in `workspaceSlug`, if there is one to take over. */
export function useLiveMentorChat(workspaceSlug: string): MentorChatSession | undefined {
	const session = use(LiveMentorChat);
	return session?.workspaceSlug === workspaceSlug ? session : undefined;
}
