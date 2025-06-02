import {
	useChatData,
	useChatInteract,
	useChatMessages,
} from "@chainlit/react-client";

import { Send } from "@/components/mentor/icons/Send";
import { Stop } from "@/components/mentor/icons/Stop";
import { Button } from "@/components/ui/button";
import {
	Tooltip,
	TooltipContent,
	TooltipProvider,
	TooltipTrigger,
} from "@/components/ui/tooltip";

interface SubmitButtonProps {
	disabled?: boolean;
	onSubmit: () => void;
}

export default function SubmitButton({
	disabled,
	onSubmit,
}: SubmitButtonProps) {
	const { loading } = useChatData();
	const { firstInteraction } = useChatMessages();
	const { stopTask } = useChatInteract();

	return (
		<TooltipProvider>
			{loading && firstInteraction ? (
				<Tooltip>
					<TooltipTrigger asChild>
						<Button
							id="stop-button"
							onClick={stopTask}
							size="icon"
							className="rounded-full h-8 w-8"
						>
							<Stop className="!size-6" />
						</Button>
					</TooltipTrigger>
					<TooltipContent>
						<p>Stop Task</p>
					</TooltipContent>
				</Tooltip>
			) : (
				<Tooltip>
					<TooltipTrigger asChild>
						<Button
							id="chat-submit"
							disabled={disabled}
							onClick={onSubmit}
							size="icon"
							className="rounded-full h-8 w-8"
						>
							<Send className="!size-6" />
						</Button>
					</TooltipTrigger>
					<TooltipContent>
						<p>Send message</p>
					</TooltipContent>
				</Tooltip>
			)}
		</TooltipProvider>
	);
}
