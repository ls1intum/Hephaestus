import { defineRule } from "@oxlint/plugins";
import { propertyName } from "../property.ts";

/** The module the generated client exports its key helpers from. */
const GENERATED = "@/api/@tanstack/react-query.gen";

export const noManualQueryKey = defineRule({
	meta: {
		type: "problem",
		docs: {
			description:
				"A `queryKey` comes from the generated `*QueryKey()` helper, never from a hand-written array.",
		},
		messages: {
			handWritten: `This hand-builds the array shape \`createQueryKey\` emits in \`${GENERATED}\`, which is regenerated from the OpenAPI spec. When that shape changes nothing here fails: the key simply stops matching, so an invalidation becomes a no-op and the UI keeps serving stale data. Pass the generated key — \`getThingQueryKey({ path: … })\` — or spread the generated options.`,
		},
	},
	create(context) {
		return {
			Property(node) {
				if (propertyName(node) !== "queryKey") return;
				// A shorthand, an identifier or a call is already someone else's value; only an array
				// literal is this file claiming to know the generated shape.
				if (node.value.type !== "ArrayExpression") return;
				context.report({ node: node.value, messageId: "handWritten" });
			},
		};
	},
});
