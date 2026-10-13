/** A missing summary represents denied source authority, while the observation's facts remain. */
export function observationSummary(observation: { summary?: string }): string {
	return observation.summary ?? "Observation text unavailable";
}

export const OBSERVATION_WORDS_UNAVAILABLE =
	"The observation text and evidence are unavailable because source access could not be verified.";
