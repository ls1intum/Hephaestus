import { type VariantProps, cva } from "class-variance-authority";
import { Bot, User } from "lucide-react";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import type { UIMessage } from "@ai-sdk/react";

const messageVariants = cva(
	"max-w-[80%] rounded-lg px-4 py-3 text-sm leading-relaxed",
	{
		variants: {
			role: {
				user: "ml-auto bg-primary text-primary-foreground",
				assistant: "mr-auto bg-muted/50 text-foreground border",
			},
		},
		defaultVariants: {
			role: "assistant",
		},
	},
);

interface MessageProps extends React.ComponentProps<"div"> {
	message: UIMessage;
}

/**
 * Enhanced message component with avatars and improved styling.
 * Displays individual chat messages with proper styling for different roles.
 */
function Message({
	className,
	message,
	...props
}: MessageProps & VariantProps<typeof messageVariants>) {
	if (message.role === "system") {
		return null; // Skip system messages
	}

	const isUser = message.role === "user";

	return (
		<div
			data-slot="message"
			className={cn(
				"flex w-full gap-3 items-start",
				isUser ? "flex-row-reverse" : "flex-row",
			)}
			{...props}
		>
			{/* Avatar */}
			<Avatar className="h-8 w-8 shrink-0">
				<AvatarFallback
					className={cn(
						isUser
							? "bg-primary text-primary-foreground"
							: "bg-primary/10 text-primary",
					)}
				>
					{isUser ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
				</AvatarFallback>
			</Avatar>

			{/* Message content */}
			<div className={cn(messageVariants({ role: message.role }), className)}>
				{message.parts.map((part, index) => {
					const partKey = `${part.type}-${index}`;

					// Handle text parts - check if part has text property
					if (part.type === "text" && "text" in part) {
						return (
							<span key={partKey} className="whitespace-pre-wrap">
								{(part as { text: string }).text}
							</span>
						);
					}

					// Handle file parts
					if (part.type === "file") {
						return (
							<div
								key={partKey}
								className="mt-2 p-2 bg-muted/30 rounded text-xs"
							>
								<span className="font-medium">File attached</span>
							</div>
						);
					}

					// Skip step-start and other internal AI SDK parts
					if (part.type === "step-start") {
						return null;
					}

					// Handle reasoning parts
					if (part.type === "reasoning" && "text" in part) {
						return (
							<div
								key={partKey}
								className="mt-2 p-2 bg-blue-50 text-blue-700 rounded text-xs italic"
							>
								{(part as { text: string }).text}
							</div>
						);
					}

					// Handle tool parts
					if (part.type.startsWith("tool-")) {
						return (
							<div
								key={partKey}
								className="mt-2 p-2 bg-muted/30 rounded text-xs"
							>
								<span className="font-medium">Tool:</span> {part.type}
							</div>
						);
					}

					// Handle data parts (source documents, URLs)
					if (
						part.type.startsWith("data-") ||
						part.type === "source-url" ||
						part.type === "source-document"
					) {
						return (
							<div
								key={partKey}
								className="mt-2 p-2 bg-blue-50 rounded text-xs"
							>
								<span className="font-medium">Source:</span> {part.type}
							</div>
						);
					}

					// Skip unknown parts gracefully
					return null;
				})}
			</div>
		</div>
	);
}

export { Message, type MessageProps };
