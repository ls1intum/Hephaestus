import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

type WorkspaceStoreState = {
	selectedSlug?: string;
	hasHydrated: boolean;
	setSelectedSlug: (slug?: string) => void;
	setHasHydrated: (hasHydrated: boolean) => void;
};

export const useWorkspaceStore = create<WorkspaceStoreState>()(
	persist(
		(set) => ({
			selectedSlug: undefined,
			hasHydrated: false,
			setSelectedSlug: (slug) => set({ selectedSlug: slug }),
			setHasHydrated: (hasHydrated) => set({ hasHydrated }),
		}),
		{
			name: "hephaestus-workspace-selection",
			storage: createJSONStorage(() => localStorage),
			partialize: (state) => ({ selectedSlug: state.selectedSlug }),
			onRehydrateStorage: () => (state) => {
				state?.setHasHydrated(true);
			},
		},
	),
);
