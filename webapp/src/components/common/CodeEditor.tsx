import MonacoEditor, { loader, type EditorProps } from "@monaco-editor/react";
import { ErrorBoundary } from "@sentry/react";
import { cn } from "cn";
import * as monaco from "monaco-editor";
/* oxlint-disable import/default -- Vite's ?worker transform supplies the default worker constructor. */
import EditorWorker from "monaco-editor/editor/editor.worker.js?worker";
import TypeScriptWorkerConstructor from "monaco-editor/language/typescript/ts.worker.js?worker";
import { useId, useLayoutEffect, useRef } from "react";
import { flushSync } from "react-dom";
/* oxlint-enable import/default */

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

self.MonacoEnvironment = {
	getWorker(_workerId, label) {
		return label === "typescript" || label === "javascript"
			? new TypeScriptWorkerConstructor()
			: new EditorWorker();
	},
};
loader.config({ monaco });
// The editor has no imported modules; check syntax without reporting unresolved imports.
monaco.typescript.typescriptDefaults.setDiagnosticsOptions({
	...monaco.typescript.typescriptDefaults.getDiagnosticsOptions(),
	noSemanticValidation: true,
	noSuggestionDiagnostics: true,
});

export interface CodeEditorProps {
	value: string;
	onChange: (value: string) => void;
	language?: string;
	className?: string;
	readOnly?: boolean;
	ariaLabel?: string;
}

export function CodeEditor({
	value,
	onChange,
	language = "typescript",
	className,
	readOnly = false,
	ariaLabel,
}: CodeEditorProps) {
	const id = useId();
	const modelPath = `inmemory://hephaestus-editor/${id}/script.ts`;
	const currentValue = useRef(value);
	useLayoutEffect(() => {
		currentValue.current = value;
	}, [value]);
	const handleChange: EditorProps["onChange"] = (updated = "") => {
		if (updated !== currentValue.current) {
			// Monaco emits outside React's input events; commit the controlled value before its next edit.
			flushSync(() => onChange(updated));
		}
	};
	return (
		<div className={cn("overflow-hidden rounded-md border", className)}>
			<ErrorBoundary
				onError={() => {
					// Construction can fail after the library creates a model, before it owns an editor to dispose.
					monaco.editor.getModel(monaco.Uri.parse(modelPath))?.dispose();
				}}
				fallback={(boundary) => (
					<div className="space-y-3 p-3">
						<Alert variant="destructive" role="alert">
							<AlertTitle>The editor could not load</AlertTitle>
							<AlertDescription>
								Your script is still in the form. Try loading the editor again.
							</AlertDescription>
						</Alert>
						<Button type="button" variant="outline" onClick={() => boundary.resetError()}>
							Retry
						</Button>
					</div>
				)}
			>
				<MonacoEditor
					path={modelPath}
					value={value}
					onChange={handleChange}
					language={language}
					loading={<CodeEditorSkeleton />}
					options={{
						minimap: { enabled: false },
						lineNumbers: "on",
						scrollBeyondLastLine: false,
						fontSize: 13,
						tabSize: 2,
						wordWrap: "on",
						readOnly,
						ariaLabel,
						renderLineHighlight: "none",
						overviewRulerLanes: 0,
						hideCursorInOverviewRuler: true,
						scrollbar: { verticalScrollbarSize: 8, horizontalScrollbarSize: 8 },
						padding: { top: 8, bottom: 8 },
						automaticLayout: true,
					}}
					theme="vs-dark"
				/>
			</ErrorBoundary>
		</div>
	);
}

function CodeEditorSkeleton() {
	return (
		<div className="h-full space-y-3 p-3" aria-busy>
			<span className="sr-only">Loading the editor…</span>
			<div className="space-y-3" aria-hidden>
				<Skeleton className="h-4 w-3/4" />
				<Skeleton className="h-4 w-1/2" />
				<Skeleton className="h-4 w-2/3" />
				<Skeleton className="h-4 w-1/3" />
			</div>
		</div>
	);
}
