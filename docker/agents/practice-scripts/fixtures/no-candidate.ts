// A program that still returns the retired leads instead of a result.
import { definePracticeScript } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: {},
	// @ts-expect-error -- leads are not a result: the host refuses them, which is the point of this fixture.
	run: async () => ({
		leads: [{ at: { change: "src/cart.ts", line: 6 }, label: "An added TODO." }],
	}),
});
