import { create } from "zustand";
import type { Document } from "@/api/types.gen";

type DocumentState = {
	isStreaming: boolean;
	draft?: Document;
	updatedAt?: number;
};

type DocumentsState = {
	documents: Record<string, DocumentState>;
	setStreaming: (documentId: string, isStreaming: boolean) => void;
	setEmptyDraft: (documentId: string, params?: { title?: string; id?: string }) => void;
	appendDraftDelta: (documentId: string, delta: string) => void;
	finishDraft: (documentId: string) => void;
};

export const useDocumentsStore = create<DocumentsState>()((set) => ({
	documents: {},
	setStreaming: (documentId, isStreaming) =>
		set((state) => {
			const prev: DocumentState = state.documents[documentId] ?? {
				isStreaming: false,
			};
			return {
				documents: {
					...state.documents,
					[documentId]: { ...prev, isStreaming },
				},
			};
		}),
	setEmptyDraft: (documentId, params) =>
		set((state) => {
			const prev: DocumentState = state.documents[documentId] ?? {
				isStreaming: false,
			};
			const title = params?.title ?? prev.draft?.title ?? "Document";
			const id = params?.id ?? prev.draft?.id ?? documentId;
			return {
				documents: {
					...state.documents,
					[documentId]: {
						...prev,
						isStreaming: true,
						draft: {
							id,
							title,
							kind: "text",
							versionNumber: prev.draft?.versionNumber ?? 0,
							createdAt: prev.draft?.createdAt ?? new Date(),
							content: "",
							userId: (prev.draft?.userId as number | undefined) ?? 0,
						},
						updatedAt: Date.now(),
					},
				},
			};
		}),
	appendDraftDelta: (documentId, delta) =>
		set((state) => {
			const prev: DocumentState = state.documents[documentId] ?? {
				isStreaming: false,
			};
			const nextContent = ((prev.draft?.content as string | undefined) ?? "") + (delta ?? "");

			const draft = (
				prev.draft
					? { ...prev.draft, content: nextContent }
					: {
							id: documentId,
							title: "Document",
							kind: "text",
							versionNumber: 0,
							createdAt: new Date(),
							content: nextContent,
							userId: 0,
						}
			) satisfies Document;

			return {
				documents: {
					...state.documents,
					[documentId]: {
						...prev,
						draft: draft,
						updatedAt: Date.now(),
					},
				},
			};
		}),
	finishDraft: (documentId) =>
		set((state) => {
			const prev: DocumentState = state.documents[documentId] ?? {
				isStreaming: false,
			};
			return {
				documents: {
					...state.documents,
					[documentId]: { ...prev, isStreaming: false, updatedAt: Date.now() },
				},
			};
		}),
}));
