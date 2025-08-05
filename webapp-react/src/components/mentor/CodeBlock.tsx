import { useTheme } from "@/integrations/theme";
import { type ReactNode, useEffect, useState } from "react";
import { codeToHtml } from "shiki";

interface CodeBlockProps {
	inline: boolean;
	className: string;
	children: ReactNode;
	node?: unknown; // React Markdown node object
}

// Extract language from className (e.g., "language-typescript" -> "typescript")
function extractLanguage(className: string): string {
	const match = className?.match(/language-(\w+)/);
	return match ? match[1] : "text";
}

export function CodeBlock({
	inline,
	className,
	children,
	node, // Extract node to avoid passing it as HTML attribute
	...props
}: CodeBlockProps) {
	const [highlightedCode, setHighlightedCode] = useState<string>("");
	const { theme } = useTheme();

	const codeString = String(children).replace(/\n$/, "");
	const language = extractLanguage(className || "");

	useEffect(() => {
		// Only highlight code blocks (not inline code) and when we have actual code
		if (!inline && codeString && language !== "text") {
			codeToHtml(codeString, {
				lang: language,
				theme: theme === "light" ? "github-light" : "github-dark",
			})
				.then((html) => {
					setHighlightedCode(html);
				})
				.catch((error) => {
					console.warn("Failed to highlight code:", error);
					setHighlightedCode("");
				});
		}
	}, [codeString, language, inline, theme]);

	if (!inline) {
		// For code blocks, use Shiki if available, otherwise fallback to plain
		// Always show highlighted version when available to prevent background flickering
		if (highlightedCode) {
			return (
				<div
					className="not-prose flex flex-col"
					// biome-ignore lint/security/noDangerouslySetInnerHtml: Shiki output is safe
					dangerouslySetInnerHTML={{ __html: highlightedCode }}
				/>
			);
		}

		// Fallback for unsupported languages or when no highlighting available yet
		return (
			<div className="not-prose flex flex-col">
				<pre
					{...props}
					className="text-sm w-full overflow-x-auto dark:bg-zinc-900 p-4 border border-zinc-200 dark:border-zinc-700 rounded-xl dark:text-zinc-50 text-zinc-900"
				>
					<code className="whitespace-pre-wrap break-words">{children}</code>
				</pre>
			</div>
		);
	}

	// For inline code, always use simple styling
	return (
		<code
			className={`${className} not-prose text-sm bg-zinc-100 dark:bg-zinc-800 py-0.5 px-1 rounded-md`}
			{...props}
		>
			{children}
		</code>
	);
}
