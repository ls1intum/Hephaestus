import { defineRule, type ESTree } from "@oxlint/plugins";

export const typedStoryMeta = defineRule({
	meta: {
		type: "problem",
		docs: {
			description: "A story `meta` naming a `component` must be typed `Meta<typeof That>`.",
		},
		messages: {
			untyped:
				"This `meta` names a `component` but is typed as a bare `Meta`, so its `args` are never checked against that component's props — a story can pass a prop the component does not have, and Storybook's generated controls drift with it. Type it `Meta<typeof TheComponent>`. A gallery meta that names no component is the one case `Meta` alone is right.",
		},
	},
	create(context) {
		const isBareMeta = (type: ESTree.TSType | null | undefined) =>
			type?.type === "TSTypeReference" &&
			type.typeName.type === "Identifier" &&
			type.typeName.name === "Meta" &&
			!type.typeArguments;

		const reportIfItNamesAComponent = (meta: ESTree.Expression) => {
			if (meta.type !== "ObjectExpression") return;
			const namesComponent = meta.properties.some(
				(property) =>
					property.type === "Property" &&
					!property.computed &&
					(property.key.type === "Identifier"
						? property.key.name === "component"
						: property.key.type === "Literal" && property.key.value === "component"),
			);
			if (namesComponent) context.report({ node: meta, messageId: "untyped" });
		};

		return {
			TSSatisfiesExpression(node) {
				if (isBareMeta(node.typeAnnotation)) reportIfItNamesAComponent(node.expression);
			},
			VariableDeclarator(node) {
				if (node.init && isBareMeta(node.id.typeAnnotation?.typeAnnotation)) {
					reportIfItNamesAComponent(node.init);
				}
			},
		};
	},
});
