import Editor, { type EditorProps } from "@monaco-editor/react";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

interface CodeEditorProps {
	value: string;
	onChange: (value: string) => void;
	language?: string;
	className?: string;
	readOnly?: boolean;
}

export function CodeEditor({
	value,
	onChange,
	language = "typescript",
	className,
	readOnly = false,
}: CodeEditorProps) {
	const handleChange: EditorProps["onChange"] = (newValue) => {
		onChange(newValue ?? "");
	};

	return (
		<div className={cn("rounded-md border overflow-hidden", className)}>
			<Editor
				value={value}
				onChange={handleChange}
				language={language}
				loading={
					<div className="flex items-center justify-center h-full">
						<Spinner className="h-6 w-6" />
					</div>
				}
				options={{
					minimap: { enabled: false },
					lineNumbers: "on",
					scrollBeyondLastLine: false,
					fontSize: 13,
					tabSize: 2,
					wordWrap: "on",
					readOnly,
					renderLineHighlight: "none",
					overviewRulerLanes: 0,
					hideCursorInOverviewRuler: true,
					scrollbar: {
						verticalScrollbarSize: 8,
						horizontalScrollbarSize: 8,
					},
					padding: { top: 8, bottom: 8 },
					automaticLayout: true,
				}}
				theme="vs-dark"
			/>
		</div>
	);
}
