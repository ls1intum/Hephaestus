import { closeHistory } from "prosemirror-history";
import { Plugin, PluginKey, type Transaction } from "prosemirror-state";
import { Decoration, DecorationSet, type EditorView } from "prosemirror-view";

type GhostMeta =
  | { type: "start"; pos?: number; className?: string }
  | { type: "push"; text: string }
  | { type: "flush" }
  | { type: "finish"; adoptMarks?: boolean }
  | { type: "cancel" };

type GhostState = {
  active: boolean;
  anchor: number;
  pending: string;
  className: string;
  decos: DecorationSet;
};

export const streamingGhostKey = new PluginKey<GhostState>("streamingGhost");

function makeWidget(pos: number, text: string, className: string) {
  return Decoration.widget(
    pos,
    () => {
      const span = document.createElement("span");
      span.className = className;
      span.textContent = text;
      return span;
    },
    {
      side: 1,
      key: "pm-stream-ghost",
    },
  );
}

function withGhostMeta(tr: Transaction, meta: GhostMeta) {
  return tr
    .setMeta(streamingGhostKey, meta)
    // Keep meta-only updates out of history
    .setMeta("addToHistory", false as unknown as boolean);
}

export const streamingGhostPlugin = new Plugin<GhostState>({
  key: streamingGhostKey,
  state: {
    init(_, { doc }): GhostState {
      return {
        active: false,
        anchor: doc.content.size,
        pending: "",
        className: "pm-stream-ghost",
        decos: DecorationSet.empty,
      };
    },
    apply(tr, prev, _old, newState) {
      const mappedAnchor = tr.mapping.map(prev.anchor);
      let decos = prev.decos.map(tr.mapping, tr.doc);
      let { active, pending, className } = prev;

      const meta = tr.getMeta(streamingGhostKey) as GhostMeta | undefined;
      if (meta) {
        switch (meta.type) {
          case "start": {
            active = true;
            pending = "";
            className = meta.className ?? prev.className;
            const nextAnchor = typeof meta.pos === "number" ? meta.pos : newState.doc.content.size;
            const clamped = Math.max(0, Math.min(nextAnchor, newState.doc.content.size));
            decos = DecorationSet.create(newState.doc, [makeWidget(clamped, "", className)]);
            return { active, anchor: clamped, pending, className, decos };
          }
          case "push": {
            pending = pending + meta.text;
            if (active) {
              const w = makeWidget(mappedAnchor, pending, className);
              decos = DecorationSet.create(newState.doc, [w]);
            }
            return { active, anchor: mappedAnchor, pending, className, decos };
          }
          case "flush": {
            if (active) {
              const w = makeWidget(mappedAnchor, pending, className);
              decos = DecorationSet.create(newState.doc, [w]);
            }
            return { active, anchor: mappedAnchor, pending, className, decos };
          }
          case "finish": {
            active = false;
            pending = "";
            decos = DecorationSet.empty;
            return { active, anchor: mappedAnchor, pending, className, decos };
          }
          case "cancel": {
            active = false;
            pending = "";
            decos = DecorationSet.empty;
            return { active, anchor: mappedAnchor, pending, className, decos };
          }
        }
      }

      return { active, anchor: mappedAnchor, pending, className, decos };
    },
  },
  props: {
    decorations(state) {
      return streamingGhostKey.getState(state)?.decos ?? null;
    },
  },
});

// ---- Controller -------------------------------------------------------------

type Controller = {
  start(view: EditorView, pos?: number, className?: string): void;
  push(view: EditorView, chunk: string): void;
  finish(view: EditorView, opts?: { adoptMarks?: boolean }): void;
  cancel(view: EditorView): void;
};

const buffers = new WeakMap<EditorView, { buf: string; scheduled: boolean }>();

function scheduleFlush(view: EditorView) {
  const box = buffers.get(view);
  if (!box || box.scheduled) return;
  box.scheduled = true;
  requestAnimationFrame(() => {
    const { state } = view;
    view.dispatch(withGhostMeta(state.tr, { type: "push", text: box.buf }));
    box.buf = "";
    box.scheduled = false;
  });
}

export const streamingGhost: Controller = {
  start(view, pos, className) {
    buffers.set(view, { buf: "", scheduled: false });
    view.dispatch(withGhostMeta(view.state.tr, { type: "start", pos, className }));
  },

  push(view, chunk) {
    const box = buffers.get(view);
    if (!box) return;
    box.buf += chunk;
    scheduleFlush(view);
  },

  finish(view, opts) {
    const box = buffers.get(view);
    if (box?.buf) {
      view.dispatch(withGhostMeta(view.state.tr, { type: "push", text: box.buf }));
      box.buf = "";
      box.scheduled = false;
    }

    const state = view.state;
    const gs = streamingGhostKey.getState(state);
    const anchor = gs?.anchor ?? state.doc.content.size;
    const pending = gs?.pending ?? "";

    if (!gs?.active || !pending) {
      view.dispatch(withGhostMeta(state.tr, { type: "cancel" }));
      return;
    }

    let tr = state.tr;
    if (opts?.adoptMarks) {
      const marks = state.storedMarks ?? undefined;
      // @ts-expect-error prosemirror types accept undefined here to clear stored marks
      tr = tr.ensureMarks(marks);
    }
    tr = tr.insertText(pending, anchor, anchor);
    tr = closeHistory(tr);
    view.dispatch(tr);
    view.dispatch(withGhostMeta(view.state.tr, { type: "finish", adoptMarks: !!opts?.adoptMarks }));
  },

  cancel(view) {
    const box = buffers.get(view);
    if (box) buffers.delete(view);
    view.dispatch(withGhostMeta(view.state.tr, { type: "cancel" }));
  },
};
