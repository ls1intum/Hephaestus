import { exampleSetup } from "prosemirror-example-setup";
import { closeHistory } from "prosemirror-history";
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
	const streamBufRef = useRef<string>("");
	const pendingRef = useRef<string>("");
	const rafRef = useRef<number | null>(null);
	const anchorRef = useRef<number>(0);

	const flush = () => {
		rafRef.current = null;
		const view = editorRef.current;
		if (!view) return;
		if (!pendingRef.current) return;

		const { state } = view;
		const pos = anchorRef.current;
		let tr = state.tr.insertText(pendingRef.current, pos, pos);
		tr = tr
			.setMeta("addToHistory", false)
			.setMeta("no-save", true)
			.setMeta("stream", true);
		view.dispatch(tr);
		anchorRef.current = tr.mapping.map(pos + pendingRef.current.length);
		pendingRef.current = "";
	};

	const scheduleFlush = () => {
		if (rafRef.current != null) return;
		rafRef.current = requestAnimationFrame(flush);
	};

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
					if (status === "streaming" && !transaction.getMeta("stream")) {
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

	// biome-ignore lint/correctness/useExhaustiveDependencies: flush and scheduleFlush are stable
	useEffect(() => {
		const view = editorRef.current;
		if (!view) return;

		const prevStatus = prevStatusRef.current;
		const enteringStream = prevStatus !== "streaming" && status === "streaming";
		const leavingStream = prevStatus === "streaming" && status !== "streaming";

		if (enteringStream) {
			anchorRef.current = view.state.selection.to;
			streamBufRef.current = buildContentFromDocument(view.state.doc);
		}

		if (status === "streaming") {
			const prev = streamBufRef.current;
			const next = content ?? "";
			if (next.length < prev.length) {
				const newDoc = buildDocumentFromContent(next);
				let tr = view.state.tr.replaceWith(
					0,
					view.state.doc.content.size,
					newDoc.content,
				);
				tr = tr
					.setMeta("addToHistory", false)
					.setMeta("no-save", true)
					.setMeta("stream", true);
				view.dispatch(tr);
				anchorRef.current = tr.mapping.map(newDoc.content.size);
				pendingRef.current = "";
			} else {
				const delta = next.slice(prev.length);
				if (delta) {
					pendingRef.current += delta;
					scheduleFlush();
				}
			}
			streamBufRef.current = next;
			prevStatusRef.current = status;
			return;
		}

		if (leavingStream) {
			if (pendingRef.current) {
				flush();
			}
			view.dispatch(
				closeHistory(view.state.tr)
					.setMeta("no-save", true)
					.setMeta("stream", true),
			);
			const updated = buildContentFromDocument(view.state.doc);
			onSaveContent(updated, true);
		} else {
			const currentContent = buildContentFromDocument(view.state.doc);
			if (currentContent !== content) {
				const newDoc = buildDocumentFromContent(content ?? "");
				let tr = view.state.tr.replaceWith(
					0,
					view.state.doc.content.size,
					newDoc.content,
				);
				tr = tr.setMeta("no-save", true).setMeta("stream", true);
				view.dispatch(tr);
			}
		}

		prevStatusRef.current = status;
	}, [content, status, onSaveContent]);

	return (
		<div
			className="relative w-full h-full prose dark:prose-invert max-w-none prose-lg"
			ref={containerRef}
		/>
	);
}
