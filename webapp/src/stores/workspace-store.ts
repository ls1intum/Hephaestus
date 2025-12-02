import { create } from "zustand";
import { persist } from "zustand/middleware";

type WorkspaceStoreState = {
	selectedSlug?: string;
	setSelectedSlug: (slug?: string) => void;
};

export const useWorkspaceStore = create<WorkspaceStoreState>()(
	persist(
		(set) => ({
			selectedSlug: undefined,
			setSelectedSlug: (slug) => set({ selectedSlug: slug }),
		}),
		{
			name: "hephaestus-workspace-selection",
		},
	),
);
