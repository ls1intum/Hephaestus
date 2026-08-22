import { defineRule } from "@oxlint/plugins";
import { propertyName } from "../property.ts";

export const noStoryA11yOverride = defineRule({
	meta: {
		type: "problem",
		docs: {
			description: "The accessibility check is configured once, in `.storybook/preview.ts`.",
		},
		messages: {
			localOverride:
				'`.storybook/preview.ts` runs the accessibility check at `test: "error"` for every story, so this entry can only lower that bar — and it lowers it silently: the suite still reports green while this one component stops being checked. Fix the violation, or, if a rule is genuinely wrong about a pattern the whole kit uses, disable it in `preview.ts` where the exemption is visible and has to be argued for once.',
		},
	},
	create(context) {
		return {
			Property(node) {
				if (propertyName(node) !== "parameters" || node.value.type !== "ObjectExpression") return;
				for (const parameter of node.value.properties) {
					if (parameter.type !== "Property") continue;
					if (propertyName(parameter) === "a11y") {
						context.report({ node: parameter, messageId: "localOverride" });
					}
				}
			},
		};
	},
});
