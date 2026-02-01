/**
 * AST-based code manipulation using ts-morph
 *
 * This module provides safe, AST-aware manipulation of TypeScript source files
 * for updating prompt and tool definitions from Langfuse.
 *
 * Key benefits over regex-based manipulation:
 * - Correctly handles all edge cases (nested backticks, escapes, multiline)
 * - Preserves formatting and whitespace
 * - Validates the AST structure before making changes
 * - Provides clear error messages for invalid structures
 */

import {
	type CallExpression,
	type Node,
	type NoSubstitutionTemplateLiteral,
	type ObjectLiteralExpression,
	Project,
	type PropertyAssignment,
	type SourceFile,
	SyntaxKind,
} from "ts-morph";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export interface UpdateResult {
	updated: boolean;
	error?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Project Management
// ─────────────────────────────────────────────────────────────────────────────

// Singleton project instance for performance (avoids re-parsing)
let projectInstance: Project | null = null;

function getProject(): Project {
	if (!projectInstance) {
		projectInstance = new Project({
			// Skip type checking for performance
			skipFileDependencyResolution: true,
			skipLoadingLibFiles: true,
		});
	}
	return projectInstance;
}

function getSourceFile(filePath: string): SourceFile {
	const project = getProject();

	// Remove from cache to get fresh content
	const existing = project.getSourceFile(filePath);
	if (existing) {
		project.removeSourceFile(existing);
	}

	return project.addSourceFileAtPath(filePath);
}

// ─────────────────────────────────────────────────────────────────────────────
// AST Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Find a property assignment within an object literal by name.
 */
function findPropertyAssignment(
	obj: ObjectLiteralExpression,
	propertyName: string,
): PropertyAssignment | undefined {
	return obj.getProperty(propertyName) as PropertyAssignment | undefined;
}

/**
 * Get the template literal value from a property assignment.
 * Handles both NoSubstitutionTemplateLiteral and regular template literals.
 */
function getTemplateLiteralFromProperty(
	prop: PropertyAssignment,
): NoSubstitutionTemplateLiteral | undefined {
	const initializer = prop.getInitializer();
	if (!initializer) {
		return undefined;
	}

	if (initializer.isKind(SyntaxKind.NoSubstitutionTemplateLiteral)) {
		return initializer as NoSubstitutionTemplateLiteral;
	}

	return undefined;
}

/**
 * Find a call expression by function name.
 */
function findCallExpression(
	sourceFile: SourceFile,
	functionNamePattern: RegExp,
): CallExpression | undefined {
	return sourceFile.getDescendantsOfKind(SyntaxKind.CallExpression).find((call) => {
		const expr = call.getExpression();
		if (expr.isKind(SyntaxKind.Identifier)) {
			return functionNamePattern.test(expr.getText());
		}
		return false;
	});
}

/**
 * Find the object literal argument of a function call.
 */
function getObjectLiteralArg(call: CallExpression): ObjectLiteralExpression | undefined {
	const args = call.getArguments();
	const firstArg = args[0];
	if (firstArg?.isKind(SyntaxKind.ObjectLiteralExpression)) {
		return firstArg as ObjectLiteralExpression;
	}
	return undefined;
}

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Updates
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Update the prompt template literal in a .prompt.ts file.
 *
 * Supports two patterns:
 * 1. Direct object: `export const promptName: PromptDefinition = { prompt: \`...\` }`
 * 2. Function call: `definePrompt({ prompt: \`...\` })`
 */
export function updatePromptInFile(filePath: string, newPrompt: string): UpdateResult {
	try {
		const sourceFile = getSourceFile(filePath);

		// Strategy 1: Find a variable declaration with PromptDefinition type annotation
		// that has a `prompt` property with a template literal
		const variableDeclarations = sourceFile.getDescendantsOfKind(SyntaxKind.VariableDeclaration);

		for (const varDecl of variableDeclarations) {
			const initializer = varDecl.getInitializer();
			if (!initializer?.isKind(SyntaxKind.ObjectLiteralExpression)) {
				continue;
			}

			const objLiteral = initializer as ObjectLiteralExpression;
			const promptProp = findPropertyAssignment(objLiteral, "prompt");

			if (promptProp) {
				const templateLiteral = getTemplateLiteralFromProperty(promptProp);
				if (templateLiteral) {
					const oldValue = templateLiteral.getLiteralValue();
					if (oldValue === newPrompt) {
						return { updated: false };
					}

					templateLiteral.setLiteralValue(newPrompt);
					sourceFile.saveSync();
					return { updated: true };
				}
			}
		}

		// Strategy 2: Find definePrompt() call (for alternative patterns)
		const definePromptCall = findCallExpression(sourceFile, /^definePrompt$/);
		if (definePromptCall) {
			const objArg = getObjectLiteralArg(definePromptCall);
			if (objArg) {
				const promptProp = findPropertyAssignment(objArg, "prompt");
				if (promptProp) {
					const templateLiteral = getTemplateLiteralFromProperty(promptProp);
					if (templateLiteral) {
						const oldValue = templateLiteral.getLiteralValue();
						if (oldValue === newPrompt) {
							return { updated: false };
						}

						templateLiteral.setLiteralValue(newPrompt);
						sourceFile.saveSync();
						return { updated: true };
					}
				}
			}
		}

		return {
			updated: false,
			error: "Could not find prompt: ` in object literal or definePrompt() call",
		};
	} catch (err) {
		return {
			updated: false,
			error: err instanceof Error ? err.message : String(err),
		};
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool Description Updates
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Update the tool description in a .tool.ts file.
 *
 * Supports two patterns:
 * 1. defineToolMeta({ name: "...", description: `...`, inputSchema: ... })
 * 2. defineToolMetaNoInput({ name: "...", description: `...` })
 */
export function updateToolDescriptionInFile(
	filePath: string,
	newDescription: string,
): UpdateResult {
	try {
		const sourceFile = getSourceFile(filePath);

		// Find defineToolMeta or defineToolMetaNoInput call
		const defineToolMetaCall = findCallExpression(sourceFile, /^defineToolMeta(?:NoInput)?$/);

		if (!defineToolMetaCall) {
			return { updated: false, error: "Could not find defineToolMeta() call" };
		}

		const objArg = getObjectLiteralArg(defineToolMetaCall);
		if (!objArg) {
			return { updated: false, error: "defineToolMeta() argument is not an object literal" };
		}

		const descriptionProp = findPropertyAssignment(objArg, "description");
		if (!descriptionProp) {
			return {
				updated: false,
				error: "Could not find 'description' property in defineToolMeta()",
			};
		}

		const templateLiteral = getTemplateLiteralFromProperty(descriptionProp);
		if (!templateLiteral) {
			return {
				updated: false,
				error: "description value is not a template literal (backtick string)",
			};
		}

		const oldValue = templateLiteral.getLiteralValue();
		if (oldValue === newDescription) {
			return { updated: false };
		}

		// Update the template literal value
		templateLiteral.setLiteralValue(newDescription);
		sourceFile.saveSync();

		return { updated: true };
	} catch (err) {
		return {
			updated: false,
			error: err instanceof Error ? err.message : String(err),
		};
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Parameter Description Updates
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Find a .describe() call on a Zod schema property.
 *
 * Looks for patterns like:
 *   paramName: z.number().min(1).max(50).describe("..."),
 *
 * Returns the string literal or template literal inside .describe()
 */
function findDescribeCallForParam(
	sourceFile: SourceFile,
	paramName: string,
): { node: Node; value: string } | undefined {
	// Find all property assignments with the given name
	const propertyAssignments = sourceFile
		.getDescendantsOfKind(SyntaxKind.PropertyAssignment)
		.filter((prop) => {
			const name = prop.getName();
			return name === paramName || name === `"${paramName}"` || name === `'${paramName}'`;
		});

	for (const prop of propertyAssignments) {
		// Check if the initializer chain contains a .describe() call
		const describeCalls = prop.getDescendantsOfKind(SyntaxKind.CallExpression).filter((call) => {
			const expr = call.getExpression();
			if (expr.isKind(SyntaxKind.PropertyAccessExpression)) {
				return expr.getName() === "describe";
			}
			return false;
		});

		const describeCall = describeCalls[0];
		if (describeCall) {
			const args = describeCall.getArguments();
			const arg = args[0];

			if (arg) {
				// Handle string literal
				if (arg.isKind(SyntaxKind.StringLiteral)) {
					return {
						node: arg,
						value: arg.getLiteralValue(),
					};
				}

				// Handle template literal
				if (arg.isKind(SyntaxKind.NoSubstitutionTemplateLiteral)) {
					return {
						node: arg,
						value: arg.getLiteralValue(),
					};
				}
			}
		}
	}

	return undefined;
}

/**
 * Update a parameter's .describe() call in a Zod schema.
 *
 * Handles both string literals and template literals inside .describe()
 */
export function updateParameterDescriptionInFile(
	filePath: string,
	paramName: string,
	newDescription: string,
): UpdateResult {
	try {
		const sourceFile = getSourceFile(filePath);

		const describeInfo = findDescribeCallForParam(sourceFile, paramName);
		if (!describeInfo) {
			return {
				updated: false,
				error: `Could not find ${paramName}.describe() pattern`,
			};
		}

		const { node, value } = describeInfo;
		if (value === newDescription) {
			return { updated: false };
		}

		// Update based on node type
		if (node.isKind(SyntaxKind.StringLiteral)) {
			node.setLiteralValue(newDescription);
		} else if (node.isKind(SyntaxKind.NoSubstitutionTemplateLiteral)) {
			node.setLiteralValue(newDescription);
		} else {
			return { updated: false, error: "Unsupported literal type in .describe()" };
		}

		sourceFile.saveSync();
		return { updated: true };
	} catch (err) {
		return {
			updated: false,
			error: err instanceof Error ? err.message : String(err),
		};
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Cache Management
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clear the project cache. Useful if files have been modified externally.
 */
export function clearCache(): void {
	if (projectInstance) {
		projectInstance = null;
	}
}
