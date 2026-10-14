import { $, type ExpressionTransformer, type IR } from "@hey-api/openapi-ts";

type ResolveSchema = (reference: string) => IR.SchemaObject;
type Expression = ReturnType<typeof $ | typeof $.attr>;

function datePaths(
	schema: IR.SchemaObject,
	resolve: ResolveSchema,
	path: string[] = [],
	seen = new Set<string>(),
): string[][] {
	if (schema.$ref !== undefined) {
		if (seen.has(schema.$ref)) {
			return [];
		}
		return datePaths(resolve(schema.$ref), resolve, path, new Set([...seen, schema.$ref]));
	}
	if (schema.type === "string" && (schema.format === "date" || schema.format === "date-time")) {
		return [path];
	}
	if (schema.type === "array") {
		return (schema.items ?? []).flatMap((item) => datePaths(item, resolve, [...path, "*"], seen));
	}
	return [
		...Object.entries(schema.properties ?? {}).flatMap(([name, property]) =>
			datePaths(property, resolve, [...path, name], seen),
		),
		...(schema.items ?? []).flatMap((item) => datePaths(item, resolve, path, seen)),
	];
}

function dateAssignment(path: string[], value: Expression, depth = 0): ReturnType<typeof $.if> {
	const [name, ...rest] = path;
	if (name === undefined) {
		return $.if(value).do($(value).assign($.new("Date").arg(value)));
	}
	if (name === "*") {
		const entry = `entry${depth}`;
		return $.if(value).do(
			$.for($.const(entry))
				.of(value)
				.do(dateAssignment(rest, $(entry), depth + 1)),
		);
	}
	const field = $(value).attr(name);
	return $.if(value).do(dateAssignment(rest, field, depth + 1));
}

function literalProperties(
	schema: IR.SchemaObject,
	resolve: ResolveSchema,
	seen = new Set<string>(),
): Map<string, string> {
	if (schema.$ref !== undefined) {
		if (seen.has(schema.$ref)) {
			return new Map();
		}
		return literalProperties(resolve(schema.$ref), resolve, new Set([...seen, schema.$ref]));
	}
	const literals = new Map<string, string>();
	for (const [name, property] of Object.entries(schema.properties ?? {})) {
		const value =
			property.const ?? (property.items?.length === 1 ? property.items[0]?.const : undefined);
		if (typeof value === "string") {
			literals.set(name, value);
		}
	}
	if (schema.logicalOperator === "and") {
		for (const part of schema.items ?? []) {
			for (const [name, value] of literalProperties(part, resolve, seen)) {
				literals.set(name, value);
			}
		}
	}
	return literals;
}

// The generator delegates unresolved OR unions to this public expression hook.
export const discriminatedUnionDates: ExpressionTransformer = ({
	schema,
	plugin,
	dataExpression,
}) => {
	if (
		dataExpression === undefined ||
		schema.items === undefined ||
		schema.logicalOperator === "and"
	) {
		return;
	}
	const resolve: ResolveSchema = (reference) =>
		plugin.context.resolveIrRef<IR.SchemaObject>(reference);
	const alternatives = schema.items.map((part) => ({
		part,
		literals: literalProperties(part, resolve),
	}));
	// The parser can split the union from its discriminator metadata. The concrete records
	// still declare their distinct literal field, which selects the same exact alternative.
	const discriminator = [...(alternatives[0]?.literals.keys() ?? [])].find((name) => {
		const values = alternatives.map((alternative) => alternative.literals.get(name));
		return (
			values.every((value) => value !== undefined) && new Set(values).size === alternatives.length
		);
	});
	if (discriminator === undefined) {
		return;
	}
	const branches = alternatives.flatMap(({ part, literals }) => {
		const paths = new Map<string, string[]>();
		for (const path of datePaths(part, resolve)) {
			paths.set(JSON.stringify(path), path);
		}
		if (paths.size === 0) {
			return [];
		}
		const literal = literals.get(discriminator);
		if (literal === undefined) {
			throw new Error("A union alternative lost its discriminator literal");
		}
		return [
			$.if($.binary($("value").attr(discriminator), "===", $.literal(literal))).do(
				...[...paths.values()].map((path) => dateAssignment(path, $("value"))),
			),
		];
	});
	if (branches.length === 0) {
		return;
	}
	const types = plugin.getPluginOrThrow("@hey-api/typescript");
	const transform = $.func()
		.param("value", (parameter) => {
			parameter.type($.type.or(types.api.schemaToType(types, schema)));
		})
		.do(...branches, $.return("value"));
	const converted = $(transform).call(dataExpression);
	return typeof dataExpression === "string"
		? [$.return(converted)]
		: [$(dataExpression).assign(converted)];
};
