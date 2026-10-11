// A `meta` without the contract version: the runner refuses it and says how to fix it.
import { definePracticeScript } from "../lib/practice-script.ts";

export default definePracticeScript({
	// @ts-expect-error -- `version` is missing, which is the point of this fixture.
	meta: { kinds: { unused: "Not used." } },
	async run() {
		return { leads: [] };
	},
});
