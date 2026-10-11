import { definePracticeScript } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {
		version: 1,
		models: { decision: "required" },
		kinds: { "never-shown": "Never shown: the script cannot run without its model." },
	},
	run: async () => ({ leads: [] }),
});
