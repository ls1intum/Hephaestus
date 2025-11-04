import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import {
	getMentorDocumentsByIdOptions,
	getMentorDocumentsByIdQueryKey,
	getMentorDocumentsByIdVersionsByVersionNumberOptions,
	putMentorDocumentsByIdMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Document } from "@/api/types.gen";
import type { DataPart } from "@/lib/types";
import { useArtifactStore } from "@/stores/artifact-store";
import { useDocumentsStore } from "@/stores/document-store";

export interface UseDocumentArtifactParams {
	documentId: string;
}

export interface UseDocumentArtifactReturn {
	/** Latest document snapshot */
	latest: Document | undefined;
	/** Current selected document version snapshot */
	selectedVersion: Document | undefined;
	/** Current streaming draft (if any) */
	draft?: Document | undefined;
	/** Is the current version the latest version? */
	isCurrentVersion: boolean;
	/** Is the document currently loading from the server? */
	isLoading: boolean;
	error: unknown;
	isStreaming: boolean;
	setStreaming: (isStreaming: boolean) => void;
	isSaving: boolean;
	selectedIndex: number;
	setSelectedIndex: (index: number) => void;
	canPrev: boolean;
	canNext: boolean;
	onPrevVersion: (() => void) | undefined;
	onNextVersion: (() => void) | undefined;
	onRestoreSelectedVersion: (() => void) | undefined;
	onBackToLatestVersion: (() => void) | undefined;
	/** Save current content to server (mutates latest) */
	saveContent: (content: string, debounce?: boolean) => void;
	/** Handle incoming stream parts and optimistically apply to latest content (used by useMentorChat) */
	onStreamPart: (part: DataPart) => void;
	/** Open artifact overlay */
	openOverlay: (rect: DOMRect) => void;
}

export function useDocumentArtifact({
	documentId,
}: UseDocumentArtifactParams): UseDocumentArtifactReturn {
	const { openArtifact } = useArtifactStore();
	const queryClient = useQueryClient();

	const documentState = useDocumentsStore(
		(state) => state.documents[documentId],
	);
	const draft = useDocumentsStore(
		(state) => state.documents[documentId]?.draft,
	);
	const {
		setStreaming: setDocStreaming,
		setEmptyDraft,
		appendDraftDelta,
		finishDraft,
	} = useDocumentsStore.getState();

	// Local selection state: -1 = latest
	const [selectedIndex, setSelectedIndex] = useState<number>(-1);
	const isCurrentVersion = selectedIndex < 0;

	// Streaming state
	const isStreaming = documentState?.isStreaming ?? false;
	const setStreaming = (streaming: boolean) =>
		setDocStreaming(documentId, streaming);
	const [isSaving, setIsSaving] = useState(false);

	// Queries
	const {
		data: latest,
		isLoading: loadingLatest,
		error: errorLatest,
	} = useQuery({
		...getMentorDocumentsByIdOptions({ path: { id: documentId } }),
		enabled: !isStreaming && Boolean(documentId),
	});

	// Build a continuous list from 1..latest-1 for navigation; load selected versions on demand
	const versionNumbersAsc = (() => {
		const latestNum = latest?.versionNumber ?? 0;
		if (!latestNum || latestNum <= 1) return [] as number[];
		const arr: number[] = [];
		for (let i = 1; i < latestNum; i++) arr.push(i);
		return arr;
	})();

	// Build navigable list that excludes the current latest version number to avoid duplicating "latest" (-1) in the list
	const navigableNumbersAsc = (() => {
		if (!versionNumbersAsc.length) return versionNumbersAsc;
		const latestNum = latest?.versionNumber;
		return latestNum == null
			? versionNumbersAsc
			: versionNumbersAsc.filter((n) => n !== latestNum);
	})();

	// Resolve selected version number by index in the navigable list
	const selectedVersionNumber =
		selectedIndex >= 0 ? navigableNumbersAsc[selectedIndex] : undefined;

	const {
		data: selectedVersionDoc,
		isLoading: loadingSelectedVersion,
		error: errorSelectedVersion,
	} = useQuery({
		enabled:
			selectedIndex >= 0 &&
			selectedVersionNumber != null &&
			Boolean(documentId),
		...getMentorDocumentsByIdVersionsByVersionNumberOptions({
			path: { id: documentId, versionNumber: selectedVersionNumber ?? 0 },
		}),
	});

	// Derived
	const selectedVersion = isCurrentVersion ? latest : selectedVersionDoc;
	const isLoading =
		loadingLatest || (selectedIndex >= 0 && loadingSelectedVersion);
	const error = errorLatest ?? errorSelectedVersion;
	// Navigation using the sorted navigableNumbersAsc as the source of truth
	const posInNumbers = selectedIndex >= 0 ? selectedIndex : -1;
	const hasAnyVersions = navigableNumbersAsc.length > 0;
	const canPrev = isCurrentVersion ? hasAnyVersions : posInNumbers > 0;
	const canNext = isCurrentVersion ? false : posInNumbers >= 0; // next is defined when viewing a past version

	// Keep a ref to the latest navigable list for robust functional updates
	const navRef = useRef<number[]>([]);
	useEffect(() => {
		navRef.current = navigableNumbersAsc;
	}, [navigableNumbersAsc]);

	const onPrevVersion = canPrev
		? () => {
				setSelectedIndex((prev) => {
					const nav = navRef.current;
					if (!nav.length) return prev;
					if (prev < 0) {
						const toIdx = nav.length - 1;
						return toIdx;
					}
					if (prev > 0) {
						const toIdx = prev - 1;
						return toIdx;
					}
					return prev;
				});
			}
		: undefined;

	const onNextVersion =
		!isCurrentVersion && posInNumbers >= 0
			? () => {
					setSelectedIndex((prev) => {
						const nav = navRef.current;
						if (!nav.length) return prev;
						if (prev >= 0 && prev < nav.length - 1) {
							const toIdx = prev + 1;
							return toIdx;
						}
						return -1; // newest previous -> latest
					});
				}
			: undefined;

	const onBackToLatestVersion = !isCurrentVersion
		? () => setSelectedIndex(-1)
		: undefined;

	// Restore selected version as new latest
	const { mutate: mutateRestore } = useMutation({
		...putMentorDocumentsByIdMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getMentorDocumentsByIdQueryKey({ path: { id: documentId } }),
			});
			queryClient.invalidateQueries({ queryKey: ["getDocumentVersions"] });
			setSelectedIndex(-1);
		},
	});

	const onRestoreSelectedVersion =
		!isCurrentVersion && selectedVersionDoc
			? () => {
					const selectedDoc = selectedVersionDoc;
					if (selectedDoc?.content && selectedDoc?.title) {
						mutateRestore({
							path: { id: documentId },
							body: {
								content: selectedDoc.content,
								title: selectedDoc.title,
								kind: selectedDoc.kind,
							},
						});
					}
				}
			: undefined;

	// Save latest content
	const { mutate: mutateSave } = useMutation({
		...putMentorDocumentsByIdMutation(),
		onSuccess: (data) => {
			queryClient.setQueryData(
				getMentorDocumentsByIdQueryKey({ path: { id: documentId } }),
				data,
			);
			// Also refresh versions list so the new version appears
			queryClient.invalidateQueries({ queryKey: ["getDocumentVersions"] });
		},
		onSettled: () => {
			setIsSaving(false);
		},
	});

	const debouncedRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const saveContent = (newContent: string, debounce = true) => {
		// Only allow saving when on latest and not streaming
		if (!isCurrentVersion || isStreaming) {
			return;
		}
		if (debouncedRef.current) {
			clearTimeout(debouncedRef.current);
			debouncedRef.current = null;
		}
		const doSave = () => {
			const currentTitle = latest?.title ?? "Document";
			mutateSave({
				body: { content: newContent, kind: "text", title: currentTitle },
				path: { id: documentId },
			});
		};
		// indicate saving as soon as content changes
		setIsSaving(true);
		if (debounce) {
			debouncedRef.current = setTimeout(doSave, 2000);
		} else {
			doSave();
		}
	};

	useEffect(
		() => () => {
			if (debouncedRef.current) {
				clearTimeout(debouncedRef.current);
				debouncedRef.current = null;
			}
		},
		[],
	);

	// Overlay open
	const openOverlay = (rect: DOMRect) => {
		const title = latest?.title ?? "Document";
		openArtifact(`text:${documentId}`, rect, title);
	};

	// Streaming handler
	const onStreamPart = (part: DataPart) => {
		if (part.type === "data-document-create") {
			setEmptyDraft(part.data.id, { title: part.data.title });
		}
		if (part.type === "data-document-update") {
			setEmptyDraft(part.data.id);
		}
		if (part.type === "data-document-delta") {
			const draft = useDocumentsStore.getState().documents[part.data.id]?.draft;
			const draftLength = draft?.content.length ?? 0;
			if (draftLength + part.data.delta.length > 200 && draftLength <= 200) {
				const vv = window.visualViewport || {
					offsetLeft: 0,
					offsetTop: 0,
					width: window.innerWidth,
					height: window.innerHeight,
				};

				const cx = vv.offsetLeft + vv.width / 2;
				const cy = vv.offsetTop + vv.height / 2;
				const centerRect = new DOMRect(cx - 100 / 2, cy - 100 / 2, 100, 100);
				openArtifact(`text:${part.data.id}`, centerRect, draft?.title);
			}
			appendDraftDelta(part.data.id, part.data.delta);
		}
		if (part.type === "data-document-finish") {
			finishDraft(part.data.id);
			const draft = useDocumentsStore.getState().documents[part.data.id]?.draft;
			const draftLength = draft?.content.length ?? 0;
			if (draftLength <= 200) {
				const vv = window.visualViewport || {
					offsetLeft: 0,
					offsetTop: 0,
					width: window.innerWidth,
					height: window.innerHeight,
				};

				const cx = vv.offsetLeft + vv.width / 2;
				const cy = vv.offsetTop + vv.height / 2;
				const centerRect = new DOMRect(cx - 100 / 2, cy - 100 / 2, 100, 100);
				openArtifact(`text:${part.data.id}`, centerRect, draft?.title);
			}
		}
	};

	return {
		latest,
		draft,
		selectedVersion,
		isCurrentVersion,
		isLoading,
		error,
		isStreaming,
		isSaving,
		selectedIndex,
		setStreaming,
		setSelectedIndex,
		canPrev,
		canNext,
		onPrevVersion,
		onNextVersion,
		onRestoreSelectedVersion,
		onBackToLatestVersion,
		saveContent,
		onStreamPart,
		openOverlay,
	};
}
