import { closeHistory } from "prosemirror-history";
import type { EditorView } from "prosemirror-view";

type BufferState = { pending: string; raf: number | null; anchor: number };

const buffers = new WeakMap<EditorView, BufferState>();

function flush(view: EditorView) {
	const box = buffers.get(view);
	if (!box) return;
	box.raf = null;
	if (!box.pending) return;
	const { state } = view;
	// Clamp the anchor in case the document shrank or was replaced while
	// we were buffering tokens. Inserting past the end of the document would
	// throw a RangeError, so we ensure the position is always valid.
	const pos = Math.max(0, Math.min(box.anchor, state.doc.content.size));
	let tr = state.tr.insertText(box.pending, pos, pos);
	tr = tr.setMeta("addToHistory", false as unknown as boolean);
	view.dispatch(tr);
	box.anchor = tr.mapping.map(pos + box.pending.length);
	box.pending = "";
}

export const streamingInsert = {
	start(view: EditorView, pos?: number) {
		const { doc, selection } = view.state;
		const startPos = typeof pos === "number" ? pos : selection.to;
		buffers.set(view, {
			pending: "",
			raf: null,
			anchor: Math.max(0, Math.min(startPos, doc.content.size)),
		});
	},
	push(view: EditorView, chunk: string) {
		const box = buffers.get(view);
		if (!box) return;
		box.pending += chunk;
		if (box.raf == null) {
			box.raf = requestAnimationFrame(() => flush(view));
		}
	},
	finish(view: EditorView) {
		const box = buffers.get(view);
		if (!box) return;
		if (box.pending) flush(view);
		buffers.delete(view);
		view.dispatch(closeHistory(view.state.tr));
	},
	cancel(view: EditorView) {
		const box = buffers.get(view);
		if (!box) return;
		if (box.raf != null) cancelAnimationFrame(box.raf);
		buffers.delete(view);
	},
};
