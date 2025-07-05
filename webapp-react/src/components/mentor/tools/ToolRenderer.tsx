import type { ToolUIPart } from "ai";

interface ToolRenderProps {
	part: ToolUIPart;
}

/**
 * Renders different tool types based on the part type.
 * Extensible renderer for AI SDK tool invocations and results.
 */
function ToolRenderer({ part }: ToolRenderProps) {
	return (
		<div className="mt-2 p-3 bg-blue-50 border border-blue-200 rounded-md">
			<div className="text-xs font-medium text-blue-800 mb-1">
				Tool: {part.type}
			</div>
			<details className="mt-2">
				<summary className="text-xs text-gray-600 cursor-pointer">
					Debug info
				</summary>
				<pre className="text-xs text-gray-600 mt-1 whitespace-pre-wrap">
					{JSON.stringify(part, null, 2)}
				</pre>
			</details>
		</div>
	);
}

export { ToolRenderer, type ToolRenderProps };
