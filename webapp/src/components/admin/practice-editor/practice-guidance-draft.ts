import { fromMarkdown } from "mdast-util-from-markdown";

import type { PracticeGuide, PracticeVisual } from "@/api/types.gen";

import { generateSlug } from "./constants";

/**
 * The limits the server enforces on a visual and a guide (`PracticeGuidanceRules`). The editor
 * checks only what it can see without parsing the SVG; the server stays the authority.
 */
export const MAX_SVG_BYTES = 64 * 1024;
export const MAX_SVG_SIZE = `${MAX_SVG_BYTES / 1024} KB`;
export const MAX_VISUAL_ALT_LENGTH = 300;
export const MAX_GUIDE_LENGTH = 12_000;
export const MAX_GUIDE_FIGURES = 4;

/** The description a new figure line starts with, which the author must replace before saving. */
export const FIGURE_PLACEHOLDER = "Describe the figure";

/** The form holds an absent visual or guide as empty text, so its controls stay controlled. */
export const NO_VISUAL: PracticeVisual = { svg: "", alt: "" };
export const NO_GUIDE: PracticeGuide = { markdown: "", figures: {} };

export function hasVisual(visual: PracticeVisual): boolean {
	return visual.svg.trim().length > 0;
}

export function hasGuide(guide: PracticeGuide): boolean {
	return guide.markdown.trim().length > 0 || Object.keys(guide.figures).length > 0;
}

/** The first problem with SVG markup that the browser can see without parsing it. */
export function svgProblem(svg: string): "not-svg" | "too-large" | undefined {
	if (!/<svg[\s>]/u.test(svg)) {
		return "not-svg";
	}
	// The server counts UTF-8 bytes, not characters.
	return new TextEncoder().encode(svg).byteLength > MAX_SVG_BYTES ? "too-large" : undefined;
}

/**
 * The markup of an uploaded file, or why it cannot be used. The size is checked before the file is
 * read, so a large file of any kind is never read whole.
 */
export async function readSvgFile(file: File): Promise<{ svg: string } | { problem: string }> {
	if (file.size > MAX_SVG_BYTES) {
		return {
			problem: `“${file.name}” is larger than ${MAX_SVG_SIZE}. Simplify the drawing, then try again.`,
		};
	}
	const svg = await file.text();
	return svgProblem(svg) === "not-svg"
		? { problem: `“${file.name}” is not an SVG file. Choose an SVG file.` }
		: { svg };
}

/** A figure name read off the uploaded file's name, made unique with a -2, -3 suffix. */
export function figureName(fileName: string, taken: readonly string[]): string {
	// The slug's 64-character cut can end on a hyphen, which a figure name may not.
	const stem = generateSlug(fileName.replace(/\.svg$/iu, "")).replace(/-$/u, "") || "figure";
	let name = stem;
	for (let suffix = 2; taken.includes(name); suffix += 1) {
		name = `${stem}-${suffix}`;
	}
	return name;
}

export function figureLine(name: string): string {
	return `![${FIGURE_PLACEHOLDER}](figures/${name}.svg)`;
}

/** Puts a block on a paragraph of its own: an image glued to the sentence before it renders inline. */
export function insertBlock(markdown: string, block: string, position: number): string {
	const before = markdown.slice(0, position);
	const after = markdown.slice(position);
	const lead = "\n\n".slice(before.length === 0 ? 2 : trailingBreaks(before));
	const trail = "\n\n".slice(after.length === 0 ? 2 : leadingBreaks(after));
	return `${before}${lead}${block}${trail}${after}`;
}

function trailingBreaks(text: string): number {
	return /\n*$/u.exec(text)?.[0].length ?? 0;
}

function leadingBreaks(text: string): number {
	return /^\n*/u.exec(text)?.[0].length ?? 0;
}

type MarkdownNode =
	| ReturnType<typeof fromMarkdown>
	| ReturnType<typeof fromMarkdown>["children"][number];

function figureImages(markdown: string, name: string) {
	const nodes: MarkdownNode[] = [];
	const visit = (node: MarkdownNode): void => {
		nodes.push(node);
		if ("children" in node) {
			for (const child of node.children) {
				visit(child);
			}
		}
	};
	visit(fromMarkdown(markdown));
	const definitions = new Map<string, string>();
	for (const node of nodes) {
		if (node.type === "definition" && !definitions.has(node.identifier)) {
			definitions.set(node.identifier, node.url);
		}
	}
	const path = `figures/${name}.svg`;
	const images = nodes.filter((node) => node.type === "image" || node.type === "imageReference");
	return images.filter((node) =>
		node.type === "image" ? node.url === path : definitions.get(node.identifier) === path,
	);
}

/** Removes rendered images of the figure; reference definitions and code examples stay intact. */
export function withoutFigure(markdown: string, name: string): string {
	const changedLines = new Set<number>();
	let remaining = "";
	let cursor = 0;
	let lineIndex = 0;
	for (const image of figureImages(markdown, name)) {
		const start = image.position?.start.offset;
		const end = image.position?.end.offset;
		if (start !== undefined && end !== undefined) {
			const before = markdown.slice(cursor, start);
			remaining += before;
			lineIndex += before.split("\n").length - 1;
			changedLines.add(lineIndex);
			cursor = end;
		}
	}
	remaining += markdown.slice(cursor);
	const lines: string[] = [];
	let removedLine = false;
	for (const [index, line] of remaining.split("\n").entries()) {
		if (changedLines.has(index)) {
			if (line.trim().length === 0) {
				removedLine = true;
				continue;
			}
			lines.push(line);
		} else if (!(removedLine && line.length === 0 && (lines.length === 0 || lines.at(-1) === ""))) {
			lines.push(line);
		}
		removedLine = false;
	}
	return lines.join("\n");
}

/** What the first rendered image says the figure shows, for inline and reference image syntax. */
export function figureDescription(markdown: string, name: string): string | undefined {
	const description = figureImages(markdown, name)[0]?.alt?.trim();
	return description === undefined || description.length === 0 ? undefined : description;
}

/** Whether a figure line still carries the description the editor inserted for the author to replace. */
export function hasFigurePlaceholder(markdown: string): boolean {
	return markdown.includes(`![${FIGURE_PLACEHOLDER}](`);
}
