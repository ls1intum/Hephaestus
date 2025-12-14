import { Link, useLocation } from "@tanstack/react-router";
import type { ChatThreadGroup, ChatThreadSummary } from "@/api/types.gen";

import {
	SidebarGroup,
	SidebarGroupContent,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

export interface NavMentorThreadsProps {
	threadGroups: ChatThreadGroup[];
	isLoading?: boolean;
	error?: string;
	workspaceSlug: string;
}

/**
 * Presentational component for displaying chat thread history in mentor mode.
 * This component only handles display logic and receives all data via props.
 */
export function NavMentorThreads({
	threadGroups,
	isLoading,
	error,
	workspaceSlug,
}: NavMentorThreadsProps) {
	if (isLoading) {
		return (
			<SidebarGroup>
				<SidebarGroupLabel>Chat History</SidebarGroupLabel>
				<SidebarGroupContent>
					<div className="text-sm text-muted-foreground p-2">Loading...</div>
				</SidebarGroupContent>
			</SidebarGroup>
		);
	}

	if (error) {
		return (
			<SidebarGroup>
				<SidebarGroupLabel>Chat History</SidebarGroupLabel>
				<SidebarGroupContent>
					<div className="text-sm text-destructive p-2">{error}</div>
				</SidebarGroupContent>
			</SidebarGroup>
		);
	}

	if (!threadGroups || threadGroups.length === 0) {
		return (
			<SidebarGroup>
				<SidebarGroupLabel>Chat History</SidebarGroupLabel>
				<SidebarGroupContent>
					<div className="text-sm text-muted-foreground p-2">
						No conversations yet
					</div>
				</SidebarGroupContent>
			</SidebarGroup>
		);
	}

	return (
		<>
			{threadGroups.map((group) => (
				<ThreadGroup
					key={group.groupName}
					title={group.groupName}
					threads={group.threads}
					workspaceSlug={workspaceSlug}
				/>
			))}
		</>
	);
}

function ThreadGroup({
	title,
	threads,
	workspaceSlug,
}: {
	title: string;
	threads: ChatThreadSummary[];
	workspaceSlug: string;
}) {
	const location = useLocation();

	if (threads.length === 0) {
		return null;
	}

	return (
		<SidebarGroup>
			<SidebarGroupLabel>{title}</SidebarGroupLabel>
			<SidebarGroupContent>
				<SidebarMenu>
					{threads.map((thread) => {
						const threadPath = `/w/${workspaceSlug}/mentor/${thread.id}`;
						const isActive = location.pathname === threadPath;

						return (
							<SidebarMenuItem key={thread.id}>
								<SidebarMenuButton asChild isActive={isActive}>
									<Link
										to="/w/$workspaceSlug/mentor/$threadId"
										params={{ workspaceSlug, threadId: thread.id }}
									>
										{thread.title}
									</Link>
								</SidebarMenuButton>
							</SidebarMenuItem>
						);
					})}
				</SidebarMenu>
			</SidebarGroupContent>
		</SidebarGroup>
	);
}
