import { memo, useEffect, useState } from "react";
import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { CodeBlock } from "./CodeBlock";
import styles from "./Markdown.module.css";

const components: Partial<Components> = {
	code: ({ children, className, ...props }) => {
		// Check if this is inline code (no className means inline code)
		const isInline = !className || !className.startsWith("language-");
		return (
			<CodeBlock
				inline={isInline}
				className={`${styles.noFade} ${className || ""}`}
				{...props}
			>
				{children}
			</CodeBlock>
		);
	},
	pre: ({ children }) => <>{children}</>,
	ol: ({ node, children, ...props }) => {
		return (
			<ol className="list-decimal list-outside ml-4" {...props}>
				{children}
			</ol>
		);
	},
	li: ({ node, children, ...props }) => {
		// Check if this is a task list item
		const isTaskListItem = (
			props as { className?: string }
		)?.className?.includes("task-list-item");

		if (isTaskListItem) {
			// Find the checkbox input in children
			const childArray = Array.isArray(children) ? children : [children];
			let checkboxChecked = false;
			let hasCheckbox = false;

			// Look for input checkbox in children
			const processedChildren = childArray.map(
				(child: unknown, index: number) => {
					const element = child as {
						type?: string;
						props?: { type?: string; checked?: boolean };
					};
					if (
						element?.type === "input" &&
						element?.props?.type === "checkbox"
					) {
						hasCheckbox = true;
						checkboxChecked = element.props.checked || false;
						// Replace with Shadcn checkbox
						return (
							<Checkbox
								key={`checkbox-${index}-${checkboxChecked}`}
								checked={checkboxChecked}
								disabled
								className="mr-1"
							/>
						);
					}
					return child;
				},
			) as React.ReactNode[];

			if (hasCheckbox) {
				return (
					<li className="flex items-start py-1 list-none" {...props}>
						{processedChildren}
					</li>
				);
			}
		}

		// Regular list item
		return (
			<li className="py-1" {...props}>
				{children}
			</li>
		);
	},
	ul: ({ node, children, ...props }) => {
		return (
			<ul className="list-disc list-outside ml-4" {...props}>
				{children}
			</ul>
		);
	},
	strong: ({ node, children, ...props }) => {
		return (
			<span className="font-semibold" {...props}>
				{children}
			</span>
		);
	},
	a: ({ node, children, ...props }) => {
		return (
			<Button asChild variant="link" size="none">
				<a target="_blank" rel="noreferrer" {...props}>
					{children}
				</a>
			</Button>
		);
	},
	table: ({ node, children, ...props }) => {
		return <Table {...props}>{children}</Table>;
	},
	thead: ({ node, children, ...props }) => {
		return <TableHeader {...props}>{children}</TableHeader>;
	},
	tbody: ({ node, children, ...props }) => {
		return <TableBody {...props}>{children}</TableBody>;
	},
	tr: ({ node, children, ...props }) => {
		return <TableRow {...props}>{children}</TableRow>;
	},
	th: ({ node, children, ...props }) => {
		return <TableHead {...props}>{children}</TableHead>;
	},
	td: ({ node, children, ...props }) => {
		return <TableCell {...props}>{children}</TableCell>;
	},
	h1: ({ node, children, ...props }) => {
		return (
			<h1 className="text-3xl font-semibold mt-6 mb-2" {...props}>
				{children}
			</h1>
		);
	},
	h2: ({ node, children, ...props }) => {
		return (
			<h2 className="text-2xl font-semibold mt-6 mb-2" {...props}>
				{children}
			</h2>
		);
	},
	h3: ({ node, children, ...props }) => {
		return (
			<h3 className="text-xl font-semibold mt-6 mb-2" {...props}>
				{children}
			</h3>
		);
	},
	h4: ({ node, children, ...props }) => {
		return (
			<h4 className="text-lg font-semibold mt-6 mb-2" {...props}>
				{children}
			</h4>
		);
	},
	h5: ({ node, children, ...props }) => {
		return (
			<h5 className="text-base font-semibold mt-6 mb-2" {...props}>
				{children}
			</h5>
		);
	},
	h6: ({ node, children, ...props }) => {
		return (
			<h6 className="text-sm font-semibold mt-6 mb-2" {...props}>
				{children}
			</h6>
		);
	},
};

const remarkPlugins = [remarkGfm];

// Streaming fade duration in milliseconds (similar to GitHub's implementation)
const STREAMING_FADE_DURATION_MS = 750;

interface MarkdownProps {
	children: string;
	isStreaming?: boolean;
}

const NonMemoizedMarkdown = ({
	children,
	isStreaming = false,
}: MarkdownProps) => {
	// Track the content hash to detect actual content changes vs re-renders
	const [lastContentHash, setLastContentHash] = useState<string>("");
	const [isAnimating, setIsAnimating] = useState(false);

	// Create a simple hash of the content to detect real changes
	const contentHash = children.length.toString() + children.slice(-50);

	useEffect(() => {
		if (isStreaming && contentHash !== lastContentHash) {
			// Content actually changed during streaming - trigger animation
			setIsAnimating(true);
			setLastContentHash(contentHash);

			// Remove animation class after animation completes
			const timeout = setTimeout(() => {
				setIsAnimating(false);
			}, STREAMING_FADE_DURATION_MS);

			return () => clearTimeout(timeout);
		}

		if (!isStreaming && isAnimating) {
			// Streaming stopped - clean up animation immediately
			setIsAnimating(false);
		}
	}, [isStreaming, contentHash, lastContentHash, isAnimating]);

	const containerClassName = [
		styles.markdownContainer,
		isAnimating && isStreaming ? styles.streamingFadeIn : "",
	]
		.filter(Boolean)
		.join(" ");

	return (
		<div className={containerClassName}>
			<ReactMarkdown remarkPlugins={remarkPlugins} components={components}>
				{children}
			</ReactMarkdown>
		</div>
	);
};

export const Markdown = memo(NonMemoizedMarkdown, (prevProps, nextProps) => {
	// During streaming, allow re-renders to show new content
	if (nextProps.isStreaming || prevProps.isStreaming) {
		return (
			prevProps.children === nextProps.children &&
			prevProps.isStreaming === nextProps.isStreaming
		);
	}

	// When not streaming, only re-render if content actually changed
	return prevProps.children === nextProps.children;
});
