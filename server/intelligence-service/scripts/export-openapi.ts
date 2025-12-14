import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import YAML from "yaml";
import { EXPORTED_TAG } from "@/shared/http/exported-tag";
import { openAPIConfig } from "@/shared/http/openapi";
import app from "../src/app";

type OpenAPISpec = {
	paths?: Record<string, Record<string, Operation>>;
	components?: { schemas?: Record<string, Schema> };
};

type Operation = {
	tags?: string[];
	operationId?: string;
	requestBody?: { content?: Record<string, { schema?: SchemaRef }> };
	responses?: Record<string, { content?: Record<string, { schema?: SchemaRef }> }>;
	[key: string]: unknown;
};

type Schema = {
	$ref?: string;
	type?: string;
	items?: SchemaRef;
	properties?: Record<string, SchemaRef>;
	allOf?: SchemaRef[];
	oneOf?: SchemaRef[];
	anyOf?: SchemaRef[];
	[key: string]: unknown;
};

type SchemaRef = Schema | { $ref: string };

const EXPORT_MARKER = { "x-hephaestus": { export: true } };

/**
 * Extract schema name from a $ref like "#/components/schemas/MySchema"
 */
function extractSchemaName(ref: string): string | null {
	const match = ref.match(/^#\/components\/schemas\/(.+)$/);
	return match?.[1] ?? null;
}

/**
 * Recursively collect all schema names referenced by a schema.
 */
function collectReferencedSchemas(
	schema: SchemaRef | undefined,
	schemas: Record<string, Schema>,
	collected: Set<string>,
): void {
	if (!schema) {
		return;
	}

	if ("$ref" in schema && schema.$ref) {
		const name = extractSchemaName(schema.$ref);
		if (name && !collected.has(name)) {
			collected.add(name);
			// Recursively collect schemas referenced by this schema
			const referencedSchema = schemas[name];
			if (referencedSchema) {
				collectReferencedSchemas(referencedSchema, schemas, collected);
			}
		}
		return;
	}

	// Handle inline schemas
	const s = schema as Schema;
	if (s.items) {
		collectReferencedSchemas(s.items, schemas, collected);
	}
	if (s.properties) {
		for (const prop of Object.values(s.properties)) {
			collectReferencedSchemas(prop, schemas, collected);
		}
	}
	for (const key of ["allOf", "oneOf", "anyOf"] as const) {
		if (s[key]) {
			for (const item of s[key] as SchemaRef[]) {
				collectReferencedSchemas(item, schemas, collected);
			}
		}
	}
}

/**
 * Find all operations tagged with EXPORTED_TAG and collect their schemas.
 */
function processExportedRoutes(spec: OpenAPISpec): {
	exportedOperations: Set<string>;
	exportedSchemas: Set<string>;
} {
	const exportedOperations = new Set<string>();
	const exportedSchemas = new Set<string>();
	const schemas = spec.components?.schemas ?? {};

	for (const pathItem of Object.values(spec.paths ?? {})) {
		for (const operation of Object.values(pathItem)) {
			if (typeof operation !== "object" || !operation) {
				continue;
			}

			// Check if operation has the exported tag
			const tags = operation.tags ?? [];
			if (!tags.some((tag) => EXPORTED_TAG.includes(tag as (typeof EXPORTED_TAG)[number]))) {
				continue;
			}

			// Mark operation for export
			if (operation.operationId) {
				exportedOperations.add(operation.operationId);
			}

			// Collect schemas from request body
			if (operation.requestBody?.content) {
				for (const content of Object.values(operation.requestBody.content)) {
					collectReferencedSchemas(content.schema, schemas, exportedSchemas);
				}
			}

			// Collect schemas from responses
			for (const response of Object.values(operation.responses ?? {})) {
				if (response?.content) {
					for (const content of Object.values(response.content)) {
						collectReferencedSchemas(content.schema, schemas, exportedSchemas);
					}
				}
			}
		}
	}

	return { exportedOperations, exportedSchemas };
}

/**
 * Add x-hephaestus export tags to operations and schemas.
 */
function addExportTags(spec: OpenAPISpec): {
	opCount: number;
	schemaCount: number;
} {
	const { exportedOperations, exportedSchemas } = processExportedRoutes(spec);

	// Tag schemas
	const schemas = spec.components?.schemas ?? {};
	for (const [name, schema] of Object.entries(schemas)) {
		if (exportedSchemas.has(name)) {
			Object.assign(schema, EXPORT_MARKER);
		}
	}

	// Tag operations
	for (const pathItem of Object.values(spec.paths ?? {})) {
		for (const operation of Object.values(pathItem)) {
			if (
				typeof operation === "object" &&
				operation !== null &&
				"operationId" in operation &&
				exportedOperations.has(operation.operationId as string)
			) {
				Object.assign(operation, EXPORT_MARKER);
			}
		}
	}

	return {
		opCount: exportedOperations.size,
		schemaCount: exportedSchemas.size,
	};
}

function main() {
	const spec = app.getOpenAPI31Document(openAPIConfig) as unknown as OpenAPISpec;

	// Add export tags for application-server integration
	const { opCount, schemaCount } = addExportTags(spec);
	console.log(`Tagged ${opCount} operations and ${schemaCount} schemas for export`);

	const yaml = YAML.stringify(spec);
	const outPath = resolve(process.cwd(), "openapi.yaml");
	writeFileSync(outPath, yaml, { encoding: "utf-8" });
	console.log(`OpenAPI spec written to ${outPath}`);
}

try {
	main();
} catch (err) {
	console.error("Failed to export OpenAPI spec:", err);
	process.exit(1);
}
