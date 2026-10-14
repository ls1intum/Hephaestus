import type { Citation } from "../../lib/core/contract.ts";
import { domainAccess } from "../../lib/core/domain-access.ts";
import {
	UnratedError,
	type ModelNeeds,
	type PracticeScriptContext,
} from "../../lib/core/practice-script.ts";
import { evidenceTools } from "./evidence-tools.ts";
import type { GrepMatch } from "./grep.ts";
import {
	repositoryRecordSchema,
	fileRecordSchema,
	pullRequestRecordSchema,
	issueRecordSchema,
} from "./records.ts";
import type { DiffFile } from "./types.ts";

export {
	COMMIT,
	isCommit,
	repositoryRecordSchema,
	fileRecordSchema,
	pullRequestRecordSchema,
	issueRecordSchema,
	type ScmFileRecord,
} from "./records.ts";
export type { DiffFile } from "./types.ts";
export { addedComments, commentKinds, type AddedComment } from "./comment-kinds.ts";
export interface GrepOptions {
	glob?: string;
	maxResults?: number;
	fixedString?: boolean;
}
export interface FileTree {
	readonly revision: string;
	read: (
		path: string,
		options?: { offsetLine?: number; maxLines?: number },
	) => Promise<
		| { status: "FOUND"; text: string; truncated: boolean }
		| { status: "BINARY" }
		| { status: "ABSENT" }
	>;
	list: (glob?: string) => Promise<{ paths: readonly string[]; truncated: boolean }>;
	grep: (
		pattern: string,
		options?: GrepOptions,
	) => Promise<{ matches: readonly GrepMatch[]; truncated: boolean }>;
	cite: (path: string, startLine: number, endLine?: number) => Promise<Citation>;
}
export interface ChangeView {
	readonly base: string;
	readonly head: string;
	readonly files: ReadonlyMap<string, DiffFile>;
	cite: (
		path: string,
		side: "OLD" | "NEW",
		startLine: number,
		endLine?: number,
	) => Promise<Citation>;
}
export interface Files {
	head: FileTree;
	base: FileTree | null;
	change: ChangeView | null;
}

export function requireChange(files: Files | null): ReadonlyMap<string, DiffFile> {
	if (files?.change == null) {
		throw new UnratedError("unavailable", "the change was not captured");
	}
	return files.change.files;
}

export function scm<M extends ModelNeeds>(ctx: PracticeScriptContext<M>) {
	const { files, port } = domainAccess(ctx);
	const treeOf = (side: "head" | "base", revision: string): FileTree => ({
		revision,
		read: async (path, options = {}) => port.read({ op: "read", tree: side, path, ...options }),
		list: async (glob) =>
			port.read({ op: "list", tree: side, ...(glob === undefined ? {} : { glob }) }),
		grep: async (pattern, options = {}) =>
			port.read({ op: "grep", tree: side, pattern, ...options }),
		cite: async (path, startLine, endLine) =>
			port.read({
				op: "cite",
				source: "tree",
				tree: side,
				path,
				startLine,
				...(endLine === undefined ? {} : { endLine }),
			}),
	});
	const tree: Files | null =
		files === null
			? null
			: {
					head: treeOf("head", files.head.revision),
					base: null,
					change:
						files.change === null
							? null
							: {
									...files.change,
									cite: async (path, side, startLine, endLine) =>
										port.read({
											op: "cite",
											source: "change",
											path,
											side,
											startLine,
											...(endLine === undefined ? {} : { endLine }),
										}),
								},
				};
	ctx.evidence.tools = evidenceTools(tree?.head ?? null);
	switch (ctx.kind) {
		case "scm.pull_request": {
			return {
				...ctx,
				kind: "scm.pull_request" as const,
				item: pullRequestRecordSchema.parse(ctx.item),
				files: tree,
			};
		}
		case "scm.pull_request_review": {
			return {
				...ctx,
				kind: "scm.pull_request_review" as const,
				item: pullRequestRecordSchema.parse(ctx.item),
				files: tree,
			};
		}
		case "scm.issue": {
			return {
				...ctx,
				kind: "scm.issue" as const,
				item: issueRecordSchema.parse(ctx.item),
				files: tree,
			};
		}
		case "scm.file": {
			return {
				...ctx,
				kind: "scm.file" as const,
				item: fileRecordSchema.parse(ctx.item),
				files: tree,
			};
		}
		case "scm.repository": {
			return {
				...ctx,
				kind: "scm.repository" as const,
				item: repositoryRecordSchema.parse(ctx.item),
				files: tree,
			};
		}
		default: {
			throw new Error(`the SCM kit cannot read ${ctx.kind}`);
		}
	}
}

export function expectKind<C extends { kind: string }, K extends C["kind"]>(
	ctx: C,
	kind: K,
): asserts ctx is Extract<C, { kind: K }> {
	if (ctx.kind !== kind) {
		throw new Error(`the script reads ${kind}, but the item is ${ctx.kind}`);
	}
}
