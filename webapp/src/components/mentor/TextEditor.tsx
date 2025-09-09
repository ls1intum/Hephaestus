import { exampleSetup } from "prosemirror-example-setup";
import { inputRules } from "prosemirror-inputrules";
import { EditorState } from "prosemirror-state";
import { EditorView } from "prosemirror-view";
import { useEffect, useRef } from "react";
import "prosemirror-view/style/prosemirror.css";

import {
	documentSchema,
	handleTransaction,
	headingRule,
} from "@/lib/text-editor/config";
import {
	buildContentFromDocument,
	buildDocumentFromContent,
} from "@/lib/text-editor/functions";
import { streamingInsert } from "@/lib/text-editor/streamingInsert";

type TextEditorProps = {
	content: string;
	onSaveContent: (updatedContent: string, debounce: boolean) => void;
	status: "streaming" | "idle";
	isCurrentVersion: boolean;
	currentVersionIndex: number;
};

export function TextEditor({
	content,
	onSaveContent,
	status,
	isCurrentVersion,
}: TextEditorProps) {
	const containerRef = useRef<HTMLDivElement>(null);
	const editorRef = useRef<EditorView | null>(null);
	const prevStatusRef = useRef<TextEditorProps["status"]>(status);
	const streamBufRef = useRef<string>(""); // what we have already inserted
	const streamActiveRef = useRef<boolean>(false);

	// biome-ignore lint/correctness/useExhaustiveDependencies: We only want to run this once
	useEffect(() => {
		if (containerRef.current && !editorRef.current) {
			const state = EditorState.create({
				doc: buildDocumentFromContent(content),
				plugins: [
					...exampleSetup({ schema: documentSchema, menuBar: false }),
					inputRules({
						rules: [
							headingRule(1),
							headingRule(2),
							headingRule(3),
							headingRule(4),
							headingRule(5),
							headingRule(6),
						],
					}),
				],
			});

			editorRef.current = new EditorView(containerRef.current, {
				state,
				// Non-editable while streaming or when not current
				editable: () => status !== "streaming" && isCurrentVersion,
			});
		}

		return () => {
			if (editorRef.current) {
				editorRef.current.destroy();
				editorRef.current = null;
			}
		};
		// NOTE: we only want to run this effect once
	}, []);

	useEffect(() => {
		if (editorRef.current) {
			editorRef.current.setProps({
				dispatchTransaction: (transaction) => {
					// Drop user edits while streaming to enforce read-only
					if (status === "streaming") {
						return;
					}
					handleTransaction({
						transaction,
						editorRef,
						onSaveContent,
					});
				},
				editable: () => status !== "streaming" && isCurrentVersion,
			});
		}
	}, [onSaveContent, status, isCurrentVersion]);

	useEffect(() => {
		const view = editorRef.current;
		if (!view) return;

		const prevStatus = prevStatusRef.current;
		const enteringStream = prevStatus !== "streaming" && status === "streaming";
		const leavingStream = prevStatus === "streaming" && status !== "streaming";

		if (enteringStream) {
			// Start buffering inserts at current selection end
			streamingInsert.start(view);
			streamBufRef.current = buildContentFromDocument(view.state.doc);
			streamActiveRef.current = true;
		}

		if (status === "streaming") {
			const prev = streamBufRef.current;
			const next = content ?? "";
			if (next.length < prev.length && streamActiveRef.current) {
				streamingInsert.cancel(view);
				const newDoc = buildDocumentFromContent(next);
				const tr = view.state.tr.replaceWith(
					0,
					view.state.doc.content.size,
					newDoc.content,
				);
				tr.setMeta("addToHistory", false as unknown as boolean);
				view.dispatch(tr);
				streamingInsert.start(view);
			} else if (next.length > prev.length) {
				streamingInsert.push(view, next.slice(prev.length));
			}
			streamBufRef.current = next;
			prevStatusRef.current = status;
			return;
		}

		if (leavingStream && streamActiveRef.current) {
			streamingInsert.finish(view);
			streamActiveRef.current = false;
			streamBufRef.current = "";
		} else {
			// Not streaming: ensure the document matches content
			const currentContent = buildContentFromDocument(view.state.doc);
			if (currentContent !== content) {
				const newDoc = buildDocumentFromContent(content ?? "");
				const tr = view.state.tr.replaceWith(
					0,
					view.state.doc.content.size,
					newDoc.content,
				);
				tr.setMeta("no-save", true);
				view.dispatch(tr);
			}
			streamingInsert.cancel(view);
		}

		prevStatusRef.current = status;
	}, [content, status]);

	return (
		<div
			className="relative w-full h-full prose dark:prose-invert max-w-none prose-lg"
			ref={containerRef}
		/>
	);
}
