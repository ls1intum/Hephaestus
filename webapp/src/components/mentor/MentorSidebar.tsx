import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "@tanstack/react-router";
import { MessageSquare, Plus } from "lucide-react";
import { getGroupedThreadsOptions } from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";

interface MentorSidebarProps {
	className?: string;
}

export function MentorSidebar({ className }: MentorSidebarProps) {
	const { threadId } = useParams({ strict: false });
	const { data: groupedThreads, isLoading } = useQuery(
		getGroupedThreadsOptions(),
	);

	const formatDate = (dateString: string) => {
		const date = new Date(dateString);
		const now = new Date();
		const diffInMinutes = Math.floor(
			(now.getTime() - date.getTime()) / (1000 * 60),
		);

		if (diffInMinutes < 60) {
			return `${diffInMinutes}m ago`;
		}

		const diffInHours = Math.floor(diffInMinutes / 60);
		if (diffInHours < 24) {
			return `${diffInHours}h ago`;
		}

		const diffInDays = Math.floor(diffInHours / 24);
		if (diffInDays < 7) {
			return `${diffInDays}d ago`;
		}

		return date.toLocaleDateString();
	};

	return (
		<div
			className={cn("flex flex-col h-full bg-background border-r", className)}
		>
			{/* Header */}
			<div className="p-4 border-b">
				<Link to="/mentor">
					<Button variant="outline" className="w-full justify-start">
						<Plus className="h-4 w-4 mr-2" />
						New Conversation
					</Button>
				</Link>
			</div>

			{/* Thread List */}
			<ScrollArea className="flex-1">
				<div className="p-2">
					{isLoading ? (
						<div className="flex items-center justify-center py-8">
							<div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary" />
						</div>
					) : (
						<div className="space-y-4">
							{groupedThreads?.map((group) => (
								<div key={group.groupName} className="space-y-1">
									<h3 className="text-xs font-medium text-muted-foreground px-2 py-1">
										{group.groupName}
									</h3>
									<div className="space-y-1">
										{group.threads.map((thread) => (
											<Link
												key={thread.id}
												to="/mentor/$threadId"
												params={{ threadId: thread.id }}
												className="block"
											>
												<Button
													variant={
														threadId === thread.id ? "secondary" : "ghost"
													}
													className={cn(
														"w-full justify-start text-left h-auto p-2",
														threadId === thread.id && "bg-secondary",
													)}
												>
													<MessageSquare className="h-4 w-4 mr-2 flex-shrink-0" />
													<div className="flex-1 min-w-0">
														<div className="truncate text-sm font-medium">
															{thread.title}
														</div>
														<div className="text-xs text-muted-foreground">
															{formatDate(thread.createdAt.toString())}
														</div>
													</div>
												</Button>
											</Link>
										))}
									</div>
								</div>
							))}
						</div>
					)}
				</div>
			</ScrollArea>
		</div>
	);
}
