import { Copy, RefreshCw } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
	Tooltip,
	TooltipContent,
	TooltipProvider,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import type { UIMessage } from "@ai-sdk/react";

interface MessageActionsProps extends React.ComponentProps<"div"> {
	message: UIMessage;
	onRegenerate?: () => void;
	canRegenerate?: boolean;
	className?: string;
}

/**
 * Message actions component providing copy and regenerate functionality.
 * Appears on hover for assistant messages.
 */
function MessageActions({
	className,
	message,
	onRegenerate,
	canRegenerate = false,
	...props
}: MessageActionsProps) {
	const [copied, setCopied] = useState(false);

	// Only show actions for assistant messages
	if (message.role !== "assistant") {
		return null;
	}

	const handleCopy = async () => {
		const textContent = message.parts
			.filter((part) => part.type === "text")
			.map((part) => part.text)
			.join("\n");

		if (textContent) {
			try {
				await navigator.clipboard.writeText(textContent);
				setCopied(true);
				setTimeout(() => setCopied(false), 2000);
			} catch (error) {
				// Fallback for older browsers
				console.warn("Failed to copy to clipboard:", error);
			}
		}
	};

	const handleRegenerate = () => {
		if (onRegenerate) {
			onRegenerate();
		}
	};

	return (
		<TooltipProvider>
			<div
				data-testid="message-actions"
				className={cn(
					"flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200",
					className,
				)}
				{...props}
			>
				{/* Copy button */}
				<Tooltip>
					<TooltipTrigger asChild>
						<Button
							variant="ghost"
							size="sm"
							onClick={handleCopy}
							className="h-8 w-8 p-0"
							aria-label="Copy message"
						>
							<Copy className="h-3 w-3" />
						</Button>
					</TooltipTrigger>
					<TooltipContent>
						<p>{copied ? "Copied!" : "Copy message"}</p>
					</TooltipContent>
				</Tooltip>

				{/* Regenerate button */}
				{canRegenerate && onRegenerate && (
					<Tooltip>
						<TooltipTrigger asChild>
							<Button
								variant="ghost"
								size="sm"
								onClick={handleRegenerate}
								className="h-8 w-8 p-0"
								aria-label="Regenerate response"
							>
								<RefreshCw className="h-3 w-3" />
							</Button>
						</TooltipTrigger>
						<TooltipContent>
							<p>Regenerate response</p>
						</TooltipContent>
					</Tooltip>
				)}
			</div>
		</TooltipProvider>
	);
}

export { MessageActions, type MessageActionsProps };
