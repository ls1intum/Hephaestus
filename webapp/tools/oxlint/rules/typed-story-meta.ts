import { defineRule, type ESTree } from "@oxlint/plugins";
import { propertyName } from "../property.ts";

export const typedStoryMeta = defineRule({
	meta: {
		type: "problem",
		docs: {
			description:
				"A story `meta` naming a `component` is checked against it, by `satisfies Meta<typeof That>`.",
		},
		messages: {
			untyped:
				"This `meta` names a `component` but is typed as a bare `Meta`, so its `args` are never checked against that component's props — a story can pass a prop the component does not have, and Storybook's generated controls drift with it. Type it `Meta<typeof TheComponent>`. A gallery meta that names no component is the one case `Meta` alone is right.",
			unchecked:
				"This `meta` carries no type, so nothing checks its `args` against the component's props and `StoryObj<typeof meta>` infers from an object nobody constrained. End it `satisfies Meta<typeof TheComponent>`.",
			asserted:
				"`as` asserts where `satisfies` checks: an object asserted `as Meta<typeof X>` may carry an `arg` the component has no prop for, or omit one it requires, and TypeScript accepts both. Write `satisfies Meta<typeof X>`.",
			anyArgument:
				"`Meta<any>` checks nothing a bare `Meta` would not. Name the component: `satisfies Meta<typeof TheComponent>`.",
		},
	},
	create(context) {
		const asMetaReference = (type: ESTree.TSType | null | undefined) =>
			type?.type === "TSTypeReference" &&
			type.typeName.type === "Identifier" &&
			type.typeName.name === "Meta"
				? type
				: undefined;

		const namesComponent = (meta: ESTree.Expression) =>
			meta.type === "ObjectExpression" &&
			meta.properties.some(
				(property) => property.type === "Property" && propertyName(property) === "component",
			);

		/** What a stated `Meta` type does and does not pin down, whichever way it is stated. */
		const checkStatedType = (
			meta: ESTree.TSTypeReference,
			value: ESTree.Expression | null | undefined,
		) => {
			if (!meta.typeArguments) {
				if (value && namesComponent(value)) context.report({ node: value, messageId: "untyped" });
				return;
			}
			const [component] = meta.typeArguments.params;
			if (component?.type === "TSAnyKeyword") {
				context.report({ node: meta, messageId: "anyArgument" });
			}
		};

		return {
			TSSatisfiesExpression(node) {
				const meta = asMetaReference(node.typeAnnotation);
				if (meta) checkStatedType(meta, node.expression);
			},
			TSAsExpression(node) {
				const meta = asMetaReference(node.typeAnnotation);
				if (meta) context.report({ node: meta, messageId: "asserted" });
			},
			VariableDeclarator(node) {
				const annotation = asMetaReference(node.id.typeAnnotation?.typeAnnotation);
				if (annotation) {
					checkStatedType(annotation, node.init);
					return;
				}
				// `node.id.typeAnnotation` rather than `annotation`: `const meta: StoryObj = …` states a
				// type, just not a `Meta`, and what that type checks is its own question.
				if (node.id.typeAnnotation || node.id.type !== "Identifier" || node.id.name !== "meta") {
					return;
				}
				if (node.init?.type === "ObjectExpression" && namesComponent(node.init)) {
					context.report({ node: node.init, messageId: "unchecked" });
				}
			},
		};
	},
});
