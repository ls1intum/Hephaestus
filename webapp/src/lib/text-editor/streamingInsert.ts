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
        const pos = box.anchor;
        let tr = state.tr.insertText(box.pending, pos, pos);
        tr = tr.setMeta("addToHistory", false as unknown as boolean);
        view.dispatch(tr);
        box.anchor = tr.mapping.map(pos + box.pending.length);
        box.pending = "";
}

export const streamingInsert = {
        start(view: EditorView, pos?: number) {
                buffers.set(view, {
                        pending: "",
                        raf: null,
                        anchor: typeof pos === "number" ? pos : view.state.selection.to,
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
