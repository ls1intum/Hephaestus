// A `meta` the runner refuses: the script fails before it says which models it uses.
import { definePracticeScript } from "../lib/practice-script.ts";

export default definePracticeScript({
	// @ts-expect-error -- "sometimes" is not a need, which is the point of this fixture.
	meta: { models: { decision: "sometimes" }, kinds: { unused: "Not used." } },
	async run() {
		return { leads: [] };
	},
});
