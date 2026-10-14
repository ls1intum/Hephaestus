import { isRecord } from "./pi-text.ts";
import type { CitationCapabilities } from "./work/practice-scripts/lib/core/contract.ts";

export interface CitableText {
	source: string;
	path: string;
	side: "OLD" | "NEW";
	commit: string | null;
	stagedPath: string;
	sha256: string;
}

/** The manifest owns each logical text's immutable staged bytes. */
export function citableTextIndex(value: unknown): CitableText[] {
	if (!isRecord(value) || value.citableTexts === undefined) {
		return [];
	}
	if (!Array.isArray(value.citableTexts)) {
		throw new Error("Folder index: expected a citableTexts array");
	}
	return value.citableTexts.map((entry: unknown) => {
		if (
			!isRecord(entry) ||
			typeof entry.source !== "string" ||
			!entry.source ||
			typeof entry.path !== "string" ||
			!entry.path ||
			entry.path.length > 4096 ||
			(entry.side !== "OLD" && entry.side !== "NEW") ||
			(entry.commit !== undefined &&
				entry.commit !== null &&
				(typeof entry.commit !== "string" || !entry.commit || entry.commit.length > 1024)) ||
			typeof entry.stagedPath !== "string" ||
			!entry.stagedPath ||
			entry.stagedPath.startsWith("/") ||
			entry.stagedPath.includes("\\") ||
			entry.stagedPath.split("/").some((part) => part === ".." || part === "." || !part) ||
			typeof entry.sha256 !== "string" ||
			!/^[0-9a-f]{64}$/u.test(entry.sha256)
		) {
			throw new Error("Folder index: invalid citable text proof");
		}
		return {
			source: entry.source,
			path: entry.path,
			side: entry.side,
			commit: entry.commit ?? null,
			stagedPath: entry.stagedPath,
			sha256: entry.sha256,
		};
	});
}

/** Primary readiness is not the file inventory: a folder also holds permitted work from other areas. */
export function folderCitationIndex(value: unknown): {
	availableSourceKinds: Set<string>;
	sourceFileSources: Map<string, string>;
	citationCapabilities: Map<string, CitationCapabilities>;
} {
	if (!isRecord(value) || !Array.isArray(value.sources) || !Array.isArray(value.files)) {
		throw new Error("Folder index: expected sources and files arrays");
	}
	const availableSourceKinds = new Set<string>();
	const sourceFileSources = new Map<string, string>();
	const citationCapabilities = new Map<string, CitationCapabilities>();
	for (const source of value.sources) {
		if (!isRecord(source) || typeof source.kind !== "string" || !isRecord(source.state)) {
			throw new Error("Folder index: invalid readiness source");
		}
		const flags = source.citation;
		if (
			!isRecord(flags) ||
			!["REQUIRED", "OPTIONAL", "FORBIDDEN"].includes(String(flags.side)) ||
			!["REQUIRED", "OPTIONAL", "FORBIDDEN"].includes(String(flags.commit))
		) {
			throw new Error("Folder index: source catalog citation flags are missing");
		}
		citationCapabilities.set(source.kind, {
			side: requirement(flags.side),
			commit: requirement(flags.commit),
		});
		if (source.state.availability === "AVAILABLE") {
			availableSourceKinds.add(source.kind);
		}
	}
	for (const entry of value.files) {
		if (
			!isRecord(entry) ||
			typeof entry.kind !== "string" ||
			!isRecord(entry.file) ||
			typeof entry.file.path !== "string" ||
			entry.file.path.startsWith("/") ||
			entry.file.path.split("/").some((part) => part === ".." || part === "." || part === "") ||
			entry.file.path.includes("\\")
		) {
			throw new Error("Folder index: invalid sourceFile identity");
		}
		if (sourceFileSources.has(entry.file.path)) {
			throw new Error("Folder index: duplicate sourceFile path");
		}
		// Composed views have no per-record identity; cite their canonical source files instead.
		if (
			entry.file.path.startsWith("inputs/history/") ||
			entry.file.path === "context/project_inventory.json"
		) {
			continue;
		}
		sourceFileSources.set(entry.file.path, entry.kind);
		availableSourceKinds.add(entry.kind);
	}
	return { availableSourceKinds, sourceFileSources, citationCapabilities };
}

function requirement(flag: unknown): CitationCapabilities["side"] {
	if (flag === "REQUIRED" || flag === "OPTIONAL" || flag === "FORBIDDEN") {
		return flag;
	}
	throw new Error("invalid citation requirement");
}
