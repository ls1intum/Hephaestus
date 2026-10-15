import { isJsonObject as isRecord } from "./work/practice-scripts/lib/core/json.ts";
import { z } from "./work/practice-scripts/lib/core/practice-script.ts";

/** A transport envelope carries no result fields of its own. Unwrap once; never discard sibling fields. */
export function unwrapToolArguments(value: unknown): unknown {
	if (!isRecord(value) || Object.keys(value).length !== 1) {
		return value;
	}
	const [key] = Object.keys(value);
	return key !== undefined && ["args", "arguments", "input"].includes(key) && isRecord(value[key])
		? value[key]
		: value;
}

function objectSchema(value: unknown): Record<string, unknown> {
	return isRecord(value) ? value : {};
}

function branch(schema: Record<string, unknown>, value: unknown): Record<string, unknown> {
	const variants = schema.anyOf ?? schema.oneOf;
	if (!Array.isArray(variants)) {
		return schema;
	}
	const options = variants.map(objectSchema);
	return (
		options.find((option) => {
			if (isRecord(value) && option.type === "object") {
				const properties = objectSchema(option.properties);
				return Object.entries(properties).every(([key, field]) => {
					const choices = objectSchema(field).enum;
					return !Array.isArray(choices) || choices.length !== 1 || choices.includes(value[key]);
				});
			}
			return Array.isArray(value) ? option.type === "array" : option.type === typeof value;
		}) ??
		options.find((option) => option.type !== "null") ??
		schema
	);
}

/** Examples explain shape only. They never become submitted evidence or supply a missing fact. */
function shapeExample(rawSchema: Record<string, unknown>, value: unknown): unknown {
	const schema = branch(rawSchema, value);
	if (Array.isArray(schema.enum)) {
		return schema.enum.includes(value) ? value : schema.enum[0];
	}
	if (schema.type === "object") {
		const sent = objectSchema(value);
		const properties = objectSchema(schema.properties);
		const required = Array.isArray(schema.required) ? schema.required : [];
		return Object.fromEntries(
			Object.entries(properties)
				.filter(([key]) => key in sent || required.includes(key))
				.map(([key, field]) => [key, shapeExample(objectSchema(field), sent[key])]),
		);
	}
	if (schema.type === "array") {
		const items = Array.isArray(value) ? value : [];
		const absent = schema.minItems === undefined ? [] : [undefined];
		return (items.length > 0 ? items : absent).map((item: unknown) =>
			shapeExample(objectSchema(schema.items), item),
		);
	}
	const types = Array.isArray(schema.type) ? schema.type : [schema.type];
	if (types.includes("integer") || types.includes("number")) {
		return typeof value === "number" &&
			Number.isSafeInteger(value) &&
			value >= Number(schema.minimum ?? 0)
			? value
			: Number(schema.minimum ?? 0);
	}
	if (types.includes("string")) {
		return typeof value === "string" && value.trim() !== ""
			? value.slice(0, Number(schema.maxLength ?? value.length))
			: "<copy the captured fact or exact text you read>";
	}
	return value ?? null;
}

/** Check the advertised schema before SDK coercion, with one field and a local corrected example. */
export function toolArgumentValidator(rawSchema: object, exampleSchema: object = rawSchema) {
	const schema = objectSchema(exampleSchema);
	const validator = z.fromJSONSchema(objectSchema(rawSchema));
	return (value: unknown): Record<string, unknown> => {
		const parsed = validator.safeParse(value);
		if (parsed.success && isRecord(value)) {
			return value;
		}
		const locate = (path: readonly PropertyKey[]) => {
			let atSchema = schema;
			let atValue = value;
			for (const part of path) {
				atSchema = branch(atSchema, atValue);
				atSchema = objectSchema(
					typeof part === "number"
						? atSchema.items
						: objectSchema(atSchema.properties)[String(part)],
				);
				atValue =
					Array.isArray(atValue) && typeof part === "number"
						? atValue[part]
						: objectSchema(atValue)[String(part)];
			}
			return { atSchema, atValue };
		};
		let issue = parsed.error?.issues[0];
		while (issue?.code === "invalid_union") {
			const { atSchema, atValue } = locate(issue.path);
			const variants = atSchema.anyOf ?? atSchema.oneOf;
			const index = Array.isArray(variants) ? variants.indexOf(branch(atSchema, atValue)) : 0;
			const child: z.core.$ZodIssue | undefined = issue.errors[index]?.[0];
			if (child === undefined) {
				break;
			}
			issue = { ...child, path: [...issue.path, ...child.path] };
		}
		const path = issue?.path ?? [];
		const { atSchema, atValue } = locate(path);
		const field = path.map(String).join(".") || "arguments";
		const expected = branch(atSchema, atValue);
		const fields = Object.keys(objectSchema(expected.properties));
		let expectedType = "the declared shape";
		if (Array.isArray(expected.enum)) {
			expectedType = expected.enum.join(" or ");
		} else if (Array.isArray(expected.type)) {
			expectedType = expected.type.join(" or ");
		} else if (typeof expected.type === "string") {
			expectedType = expected.type;
		}
		const bounds = [
			typeof expected.minimum === "number" ? ` >= ${expected.minimum}` : "",
			typeof expected.minItems === "number" ? ` with at least ${expected.minItems} item(s)` : "",
		].join("");
		const shape =
			fields.length > 0
				? ` Expected an object with only ${fields.join(", ")}; required: ${Array.isArray(expected.required) ? expected.required.join(", ") : "none"}.`
				: ` Expected ${expectedType}${bounds}.`;
		throw new Error(
			`${field}: ${issue?.message ?? "expected one object"}.${shape} Corrected field example: ${JSON.stringify({ [field]: shapeExample(atSchema, atValue) })}. Example shows shape only; use captured facts and exact quotes. Send the complete call again.`,
		);
	};
}
