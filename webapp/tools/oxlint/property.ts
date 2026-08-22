import type { ESTree } from "@oxlint/plugins";

/**
 * What the `Property` visitor delivers. An object literal's property is the one these rules care
 * about; the two destructuring shapes — `const { a } = x`, `({ a } = x)` — share its `type`.
 */
export type VisitedProperty =
	| ESTree.ObjectProperty
	| ESTree.BindingProperty
	| ESTree.AssignmentTargetProperty;

/**
 * The name a property is written under, or `undefined` when it has none a rule can read: a computed
 * key (`[key]:`) names whatever the expression evaluates to at runtime, and a numeric key is not the
 * identifier any of these rules are looking for.
 */
export function propertyName(property: VisitedProperty): string | undefined {
	if (property.computed) return undefined;
	if (property.key.type === "Identifier") return property.key.name;
	if (property.key.type === "Literal" && typeof property.key.value === "string") {
		return property.key.value;
	}
	return undefined;
}
