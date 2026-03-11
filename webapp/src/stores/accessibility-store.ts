import { create } from "zustand";
import { persist } from "zustand/middleware";

export type MotionPreference = "system" | "reduced" | "full";

interface AccessibilityState {
	motion: MotionPreference;
	setMotion: (motion: MotionPreference) => void;
}

export const useAccessibilityStore = create<AccessibilityState>()(
	persist(
		(set) => ({
			motion: "system",
			setMotion: (motion) => set({ motion }),
		}),
		{
			name: "hephaestus-accessibility",
		},
	),
);
