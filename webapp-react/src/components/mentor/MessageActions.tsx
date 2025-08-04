import type { ChatMessageVote } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Tooltip,
	TooltipContent,
	TooltipProvider,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { Copy, ThumbsDown, ThumbsUp } from "lucide-react";
import { memo } from "react";

interface MessageActionsProps {
	/** The text content to copy */
	messageContentToCopy: string;
	/** Current vote state for the message */
	vote?: ChatMessageVote;
	/** Whether actions are currently loading */
	isLoading?: boolean;
	/** Callback when copy action is triggered */
	onCopy: (text: string) => void;
	/** Callback when vote action is triggered */
	onVote: (isUpvote: boolean) => void;
}

function PureMessageActions({
	messageContentToCopy,
	vote,
	isLoading = false,
	onCopy,
	onVote,
}: MessageActionsProps) {
	if (isLoading) return null;
	if (!messageContentToCopy.trim()) return null;

	return (
		<TooltipProvider delayDuration={0}>
			<div className="flex flex-row gap-2">
				<Tooltip>
					<TooltipTrigger asChild>
						<Button
							className="text-muted-foreground"
							variant="outline"
							size="icon"
							onClick={() => onCopy(messageContentToCopy)}
						>
							<Copy />
						</Button>
					</TooltipTrigger>
					<TooltipContent>Copy</TooltipContent>
				</Tooltip>

				<Tooltip>
					<TooltipTrigger asChild>
						<Button
							data-testid="message-upvote"
							className="text-muted-foreground"
							disabled={vote?.isUpvoted === true}
							variant="outline"
							size="icon"
							onClick={() => onVote(true)}
						>
							<ThumbsUp />
						</Button>
					</TooltipTrigger>
					<TooltipContent>Upvote Response</TooltipContent>
				</Tooltip>

				<Tooltip>
					<TooltipTrigger asChild>
						<Button
							data-testid="message-downvote"
							className="text-muted-foreground"
							variant="outline"
							size="icon"
							disabled={vote?.isUpvoted === false}
							onClick={() => onVote(false)}
						>
							<ThumbsDown />
						</Button>
					</TooltipTrigger>
					<TooltipContent>Downvote Response</TooltipContent>
				</Tooltip>
			</div>
		</TooltipProvider>
	);
}

export const MessageActions = memo(
	PureMessageActions,
	(prevProps, nextProps) => {
		if (prevProps.messageContentToCopy !== nextProps.messageContentToCopy)
			return false;
		if (prevProps.vote?.isUpvoted !== nextProps.vote?.isUpvoted) return false;
		if (prevProps.isLoading !== nextProps.isLoading) return false;
		return true;
	},
);
