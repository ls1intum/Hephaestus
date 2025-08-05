import type { ChatMessageVote } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Tooltip,
	TooltipContent,
	TooltipProvider,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { Copy, PencilIcon, ThumbsDown, ThumbsUp } from "lucide-react";
import { memo } from "react";

interface MessageActionsProps {
	/** Optional CSS class name */
	className?: string;
	/** The text content to copy */
	messageContentToCopy: string;
	/** The role of the message (user or assistant) */
	messageRole: "user" | "assistant" | "system";
	/** Current vote state for the message */
	vote?: ChatMessageVote;
	/** Whether actions are currently loading */
	isLoading?: boolean;
	/** Whether the message is in edit mode */
	isInEditMode?: boolean;
	/** Layout variant for different contexts */
	variant?: "default" | "artifact";
	/** Callback when copy action is triggered */
	onCopy: (text: string) => void;
	/** Callback when vote action is triggered (assistant messages only) */
	onVote?: (isUpvote: boolean) => void;
	/** Callback when edit action is triggered (user messages only) */
	onEdit?: () => void;
}

function PureMessageActions({
	className,
	messageContentToCopy,
	messageRole,
	vote,
	isLoading = false,
	isInEditMode = false,
	variant = "default",
	onCopy,
	onVote,
	onEdit,
}: MessageActionsProps) {
	if (isLoading) return null;
	if (!messageContentToCopy.trim()) return null;
	if (messageRole === "user" && isInEditMode) return null;

	const isUserMessage = messageRole === "user";
	const isAssistantMessage = messageRole === "assistant";

	// Different button styling for artifact context
	const buttonClasses =
		variant === "artifact"
			? "text-muted-foreground hover:text-foreground hover:bg-primary/5"
			: "text-muted-foreground hover:text-foreground";

	// For user messages, align actions to the right
	const containerClassName = cn(
		"flex flex-row gap-0.5 opacity-0 group-hover/message:opacity-100 transition-opacity",
		{
			"justify-end": isUserMessage,
			"justify-start": isAssistantMessage,
		},
		className,
	);

	return (
		<TooltipProvider delayDuration={0}>
			<div className={containerClassName}>
				{/* Copy button for all messages */}
				<Tooltip>
					<TooltipTrigger asChild>
						<Button
							className={buttonClasses}
							variant="ghost"
							size="icon"
							onClick={() => onCopy(messageContentToCopy)}
						>
							<Copy size={14} />
						</Button>
					</TooltipTrigger>
					<TooltipContent side="bottom">Copy</TooltipContent>
				</Tooltip>

				{/* Edit button for user messages only */}
				{isUserMessage && onEdit && (
					<Tooltip>
						<TooltipTrigger asChild>
							<Button
								className={buttonClasses}
								variant="ghost"
								size="icon"
								onClick={onEdit}
							>
								<PencilIcon size={14} />
							</Button>
						</TooltipTrigger>
						<TooltipContent side="bottom">Edit message</TooltipContent>
					</Tooltip>
				)}

				{/* Vote buttons for assistant messages only */}
				{isAssistantMessage && onVote && (
					<>
						<Tooltip>
							<TooltipTrigger asChild>
								<Button
									data-testid="message-upvote"
									className={cn(
										"text-muted-foreground hover:text-github-success-foreground hover:bg-github-success-foreground/10",
										{
											"text-github-success-foreground":
												vote?.isUpvoted === true,
											"opacity-50 hover:opacity-100": vote?.isUpvoted === false,
										},
									)}
									variant="ghost"
									size="icon"
									onClick={() => onVote(true)}
								>
									<ThumbsUp size={14} />
								</Button>
							</TooltipTrigger>
							<TooltipContent side="bottom">Good response</TooltipContent>
						</Tooltip>

						<Tooltip>
							<TooltipTrigger asChild>
								<Button
									data-testid="message-downvote"
									className={cn(
										"text-muted-foreground hover:text-github-danger-foreground hover:bg-github-danger-foreground/10",
										{
											"text-github-danger-foreground":
												vote?.isUpvoted === false,
											"opacity-50 hover:opacity-100": vote?.isUpvoted === true,
										},
									)}
									variant="ghost"
									size="icon"
									onClick={() => onVote(false)}
								>
									<ThumbsDown size={14} />
								</Button>
							</TooltipTrigger>
							<TooltipContent side="bottom">Bad response</TooltipContent>
						</Tooltip>
					</>
				)}
			</div>
		</TooltipProvider>
	);
}

export const MessageActions = memo(
	PureMessageActions,
	(prevProps, nextProps) => {
		if (prevProps.messageContentToCopy !== nextProps.messageContentToCopy)
			return false;
		if (prevProps.messageRole !== nextProps.messageRole) return false;
		if (prevProps.vote?.isUpvoted !== nextProps.vote?.isUpvoted) return false;
		if (prevProps.isLoading !== nextProps.isLoading) return false;
		if (prevProps.isInEditMode !== nextProps.isInEditMode) return false;
		return true;
	},
);
