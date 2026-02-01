import { create } from "zustand";

export type UIArtifactStatus = "idle" | "streaming";

export interface ArtifactOverlay {
	/** Unique artifact id. For document artifacts use pattern `${kind}:${documentId}` */
	artifactId: string;
	/** Title displayed in overlay header */
	title: string;
	/** Whether the overlay is currently visible for this artifact */
	isVisible: boolean;
	/** Status used for UI affordances (e.g. show toolbar, skeletons) */
	status: UIArtifactStatus;
	/** Bounding box of the source element to animate from */
	boundingBox: { top: number; left: number; width: number; height: number };
}

interface ArtifactStoreState {
	/** Map of artifacts by id (we can keep multiple around, but only one visible) */
	artifacts: Record<string, ArtifactOverlay>;
	/** Id of the currently visible artifact (if any) */
	visibleArtifactId: string | null;
	/** Open (or create) an artifact and make it the only visible one */
	openArtifact: (artifactId: string, boundingBox: DOMRect, title?: string) => void;
	/** Close the currently visible artifact */
	closeArtifact: () => void;
	/** Clear only the visible id (used after exit animation) */
	clearVisibleArtifact: () => void;
	/** Remove a specific artifact from the registry */
	removeArtifact: (artifactId: string) => void;
	/** Update status for an artifact */
	setArtifactStatus: (artifactId: string, status: UIArtifactStatus) => void;
	/** Update title for an artifact */
	setArtifactTitle: (artifactId: string, title: string) => void;
	/** Selector helper to get currently visible artifact */
	getVisibleArtifact: () => ArtifactOverlay | null;
}

export const useArtifactStore = create<ArtifactStoreState>((set, get) => ({
	artifacts: {},
	visibleArtifactId: null,
	openArtifact: (artifactId, rect, title = "Artifact") =>
		set((state) => {
			const boundingBox = {
				top: rect.top,
				left: rect.left,
				width: rect.width,
				height: rect.height,
			};

			const existing = state.artifacts[artifactId];
			const artifact: ArtifactOverlay = {
				artifactId,
				title: existing?.title ?? title,
				isVisible: true,
				status: existing?.status ?? "idle",
				boundingBox,
			};

			// Hide all others, show only this one
			const nextArtifacts: Record<string, ArtifactOverlay> = Object.fromEntries(
				Object.entries(state.artifacts).map(([id, a]) => [
					id,
					{ ...a, isVisible: id === artifactId },
				]),
			);
			nextArtifacts[artifactId] = artifact;

			return { artifacts: nextArtifacts, visibleArtifactId: artifactId };
		}),
	closeArtifact: () =>
		set((state) => {
			const id = state.visibleArtifactId;
			if (!id) return state;
			const existing = state.artifacts[id];
			if (!existing) return state;
			return {
				artifacts: {
					...state.artifacts,
					[id]: { ...existing, isVisible: false },
				},
				// Keep visible id during exit animation; container will clear it later
				visibleArtifactId: id,
			};
		}),
	clearVisibleArtifact: () => set((state) => ({ ...state, visibleArtifactId: null })),
	removeArtifact: (artifactId) =>
		set((state) => {
			const next = { ...state.artifacts };
			delete next[artifactId];
			return { ...state, artifacts: next };
		}),
	setArtifactStatus: (artifactId, status) =>
		set((state) => {
			const existing = state.artifacts[artifactId];
			if (!existing) return state;
			return {
				artifacts: {
					...state.artifacts,
					[artifactId]: { ...existing, status },
				},
			};
		}),
	setArtifactTitle: (artifactId, title) =>
		set((state) => {
			const existing = state.artifacts[artifactId];
			if (!existing) return state;
			return {
				artifacts: { ...state.artifacts, [artifactId]: { ...existing, title } },
			};
		}),
	getVisibleArtifact: () => {
		const id = get().visibleArtifactId;
		if (!id) return null;
		return get().artifacts[id] ?? null;
	},
}));
