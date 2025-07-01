import { cva } from "class-variance-authority";
import { Bot, RefreshCw } from "lucide-react";

import { MemoizedMarkdown } from "@/components/shared/MemoizedMarkdown";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { UIMessage } from "@ai-sdk/react";
import type { ToolUIPart } from "ai";
import { ToolRenderer } from "./tools/ToolRenderer";

const messageVariants = cva("", {
	variants: {
		role: {
			user: "max-w-[80%] ml-auto bg-primary text-primary-foreground rounded-lg px-4 py-3 text-sm leading-relaxed",
			assistant: "text-foreground py-1",
		},
	},
	defaultVariants: {
		role: "assistant",
	},
});

// Union type for all possible message states
type MessageProps = 
	| {
			type: "message";
			message: UIMessage;
			className?: string;
	  }
	| {
			type: "loading";
			className?: string;
	  }
	| {
			type: "error";
			error: Error;
			onRetry?: () => void;
			className?: string;
	  };

/**
 * Base message wrapper that handles consistent avatar + content layout.
 */
function MessageWrapper({
	children,
	isUser = false,
	avatarVariant = "default",
	className,
}: {
	children: React.ReactNode;
	isUser?: boolean;
	avatarVariant?: "default" | "error";
	className?: string;
}) {
	return (
		<div
			className={cn(
				"flex w-full gap-3 items-start",
				isUser ? "flex-row-reverse" : "flex-row",
				className,
			)}
		>
			{/* Avatar - only for assistant messages */}
			{!isUser && (
				<Avatar className="h-8 w-8 shrink-0">
					<AvatarFallback 
						className={cn(
							avatarVariant === "error" 
								? "bg-destructive/10 text-destructive" 
								: "bg-mentor/10 text-mentor"
						)}
					>
						<Bot className="h-4 w-4" />
					</AvatarFallback>
				</Avatar>
			)}
			
			{children}
		</div>
	);
}

/**
 * Message component for displaying chat messages, loading states, and error states.
 * Handles all message types with consistent styling and proper state management.
 */
function Message(props: MessageProps) {
	// Handle loading state
	if (props.type === "loading") {
		return (
			<MessageWrapper className={props.className}>
				<div className="text-muted-foreground">
					<div className="flex items-center gap-2">
						<div className="flex gap-1">
							<div className="w-2 h-2 bg-current rounded-full animate-pulse" />
							<div className="w-2 h-2 bg-current rounded-full animate-pulse [animation-delay:0.2s]" />
							<div className="w-2 h-2 bg-current rounded-full animate-pulse [animation-delay:0.4s]" />
						</div>					
					</div>
				</div>
			</MessageWrapper>
		);
	}

	// Handle error state
	if (props.type === "error") {
		const { error, onRetry, className } = props;
		return (
			<MessageWrapper className={className} avatarVariant="error">
				<div className="text-destructive space-y-3">
					<div>
						<p className="text-sm font-medium">Something went wrong</p>
						<p className="text-xs text-muted-foreground mt-1">
							{error.message || "An unexpected error occurred. Please try again."}
						</p>
					</div>
					{onRetry && (
						<Button
							variant="outline"
							size="sm"
							onClick={onRetry}
							className="text-xs h-8"
						>
							<RefreshCw className="h-3 w-3 mr-1" />
							Try again
						</Button>
					)}
				</div>
			</MessageWrapper>
		);
	}

	// Handle regular message
	const { message, className } = props;
	
	if (message.role === "system") {
		return null; // Skip system messages
	}

	const isUser = message.role === "user";

	return (
		<MessageWrapper className={className} isUser={isUser}>
			<div className={cn(messageVariants({ role: message.role }))}>
				{message.parts.map((part, index) => {
					const partKey = `${part.type}-${index}`;

					// Handle text parts
					if (part.type === "text") {
						return (
							<MemoizedMarkdown 
								key={partKey}
								content={part.text}
								id={`${message.id}-${index}`} 
							/>
						);
					}

					// Handle tool parts (tool-call, tool-result, etc.)
					if (part.type.startsWith("tool-")) {
						return <ToolRenderer key={partKey} part={part as ToolUIPart} />;
					}

					// Skip all other part types
					return null;
				})}
			</div>
		</MessageWrapper>
	);
}

export { Message, type MessageProps };
