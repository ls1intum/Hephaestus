import { marked } from "marked";
import { memo, useMemo } from "react";
import ReactMarkdown from "react-markdown";

function parseMarkdownIntoBlocks(markdown: string): string[] {
	const tokens = marked.lexer(markdown);
	return tokens.map((token) => token.raw);
}

const MemoizedMarkdownBlock = memo(
	({ content }: { content: string }) => {
		return (
			<ReactMarkdown
				components={{
					// Minimal styling for code blocks
					pre: ({ children }) => (
						<pre className="overflow-x-auto bg-muted/50 border rounded-md p-3 text-sm my-3">
							{children}
						</pre>
					),
					code: ({ children, className }) => {
						// Inline code
						if (!className) {
							return (
								<code className="bg-muted/50 px-1 py-0.5 rounded text-sm font-mono">
									{children}
								</code>
							);
						}
						// Block code
						return <code className="font-mono text-sm">{children}</code>;
					},
					// Remove margins from paragraphs for better chat flow
					p: ({ children }) => <p className="mb-3 last:mb-0">{children}</p>,
					// Style lists better for chat
					ul: ({ children }) => (
						<ul className="mb-3 last:mb-0 ml-4 list-disc">{children}</ul>
					),
					ol: ({ children }) => (
						<ol className="mb-3 last:mb-0 ml-4 list-decimal">{children}</ol>
					),
					// Style headings for chat context
					h1: ({ children }) => (
						<h1 className="text-lg font-semibold mb-3 mt-6 first:mt-0">
							{children}
						</h1>
					),
					h2: ({ children }) => (
						<h2 className="text-base font-semibold mb-2 mt-5 first:mt-0">
							{children}
						</h2>
					),
					h3: ({ children }) => (
						<h3 className="text-sm font-semibold mb-2 mt-4 first:mt-0">
							{children}
						</h3>
					),
					// Style blockquotes
					blockquote: ({ children }) => (
						<blockquote className="border-l-2 border-muted-foreground/30 pl-4 my-3 italic text-muted-foreground">
							{children}
						</blockquote>
					),
				}}
			>
				{content}
			</ReactMarkdown>
		);
	},
	(prevProps, nextProps) => {
		if (prevProps.content !== nextProps.content) return false;
		return true;
	},
);

MemoizedMarkdownBlock.displayName = "MemoizedMarkdownBlock";

export const MemoizedMarkdown = memo(
	({ content, id }: { content: string; id: string }) => {
		const blocks = useMemo(() => parseMarkdownIntoBlocks(content), [content]);

		return blocks.map((block, index) => {
			// Create stable key with content prefix to avoid index-only keys
			const blockKey = `${id}-${block.substring(0, 10).replace(/\W/g, "")}-${index}`;
			return <MemoizedMarkdownBlock content={block} key={blockKey} />;
		});
	},
);

MemoizedMarkdown.displayName = "MemoizedMarkdown";
