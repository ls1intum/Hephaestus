import { type VariantProps, cva } from "class-variance-authority";

import { cn } from "@/lib/utils";

const messageVariants = cva(
	"max-w-[80%] rounded-lg px-4 py-2 text-sm leading-relaxed",
	{
		variants: {
			role: {
				user: "ml-auto bg-primary text-primary-foreground",
				assistant: "mr-auto bg-muted text-foreground",
			},
		},
		defaultVariants: {
			role: "assistant",
		},
	},
);

interface MessagePart {
	type: string;
	text?: string;
	data?: unknown;
	mediaType?: string;
	details?: Array<{ type: string; text?: string }>;
	source?: { id: string; url: string; title?: string };
	url?: string;
	title?: string;
	id?: string;
}

interface MessageProps extends React.ComponentProps<"div"> {
	role: "user" | "assistant";
	parts: MessagePart[];
	isStreaming?: boolean;
}

/**
 * Message component displays individual chat messages with proper styling for different roles.
 */
function Message({
	className,
	role,
	parts,
	isStreaming = false,
	...props
}: MessageProps & VariantProps<typeof messageVariants>) {
	return (
		<div
			data-slot="message"
			className={cn(
				"flex w-full",
				role === "user" ? "justify-end" : "justify-start",
			)}
			{...props}
		>
			<div className={cn(messageVariants({ role }), className)}>
				{parts.map((part, index) => {
					const partKey = `${part.type}-${index}`;

					if (part.type === "text") {
						return (
							<span key={partKey} className="whitespace-pre-wrap">
								{part.text}
							</span>
						);
					}

					if (
						part.type === "file" &&
						part.mediaType?.startsWith("image/") &&
						typeof part.data === "string"
					) {
						return (
							<img
								key={partKey}
								src={`data:${part.mediaType};base64,${part.data}`}
								alt="AI generated content"
								className="mt-2 rounded-md max-w-full"
							/>
						);
					}

					if (part.type === "reasoning") {
						return (
							<details key={partKey} className="mt-2">
								<summary className="cursor-pointer text-xs text-muted-foreground">
									Reasoning
								</summary>
								<pre className="mt-1 text-xs whitespace-pre-wrap bg-background/50 rounded p-2">
									{part.details
										?.map((detail) =>
											detail.type === "text" ? detail.text : "<redacted>",
										)
										.join("")}
								</pre>
							</details>
						);
					}

					if (part.type === "source" || part.type === "source-url") {
						const sourceUrl = part.source?.url || part.url;
						const sourceTitle = part.source?.title || part.title;

						if (!sourceUrl) return null;

						return (
							<a
								key={partKey}
								href={sourceUrl}
								target="_blank"
								rel="noopener noreferrer"
								className="inline-block mt-1 text-xs text-primary hover:underline"
							>
								[{sourceTitle ?? new URL(sourceUrl).hostname}]
							</a>
						);
					}

					// Handle unknown part types gracefully
					if (part.text) {
						return (
							<span key={partKey} className="whitespace-pre-wrap">
								{part.text}
							</span>
						);
					}

					return null;
				})}
				{isStreaming && (
					<span className="inline-block w-2 h-4 ml-1 bg-foreground animate-pulse" />
				)}
			</div>
		</div>
	);
}

export { Message, messageVariants, type MessageProps, type MessagePart };
