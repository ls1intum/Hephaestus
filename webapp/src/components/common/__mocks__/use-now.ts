import { STORY_NOW } from "@/stories/story-clock";

/** Storybook's `useNow`: the story clock, so a story renders at the instant its fixtures were built for. */
export function useNow(): number {
	return STORY_NOW;
}
