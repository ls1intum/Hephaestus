import { readFileSync } from "node:fs";
import path from "node:path";

/** Only literal words are accepted. Shell syntax is never interpreted by the receipt parser. */
function words(command: string): string[] | undefined {
	if (/[\r\n]/u.test(command)) {
		return undefined;
	}
	const tokens = command.match(/'[^'\n]*'|"[^"$`\\\n]*"|[a-zA-Z0-9_./:@%+=,-]+/gu) ?? [];
	let rest = command;
	for (const token of tokens) {
		rest = rest.trimStart();
		if (!rest.startsWith(token)) {
			return undefined;
		}
		rest = rest.slice(token.length);
		if (rest !== "" && !/^\s/u.test(rest)) {
			return undefined;
		}
	}
	return rest.trim() === ""
		? tokens.map((token) => (/^['"]/u.test(token) ? token.slice(1, -1) : token))
		: undefined;
}

export interface BashRead {
	path: string;
	kind: "cat" | "head" | "tail" | "sed" | "nl" | "search";
	start?: number;
	end?: number;
	count?: number;
}

export function bashRead(command: string): BashRead | undefined {
	// Suppressing stderr does not change the file content or its displayed range.
	const tokens = words(command.replace(/\s+2>\s*\/dev\/null\s*$/u, ""));
	if (tokens === undefined) {
		return undefined;
	}
	const [tool, ...args] = tokens;
	const file = args.at(-1);
	if (file === undefined || file === "" || file.startsWith("-")) {
		return undefined;
	}
	if ((tool === "cat" || tool === "nl") && args.length === 1) {
		return { path: file, kind: tool };
	}
	if ((tool === "head" || tool === "tail") && args.length === 3 && args[0] === "-n") {
		const count = Number(args[1]);
		if (/^\d+$/u.test(args[1] ?? "") && Number.isSafeInteger(count) && count > 0) {
			return { path: file, kind: tool, count };
		}
	}
	if (tool === "sed" && args.length === 3 && args[0] === "-n") {
		const range = /^(?<start>\d+),(?<end>\d+)p$/u.exec(args[1] ?? "");
		if (range !== null) {
			const start = Number(range.groups?.start);
			const end = Number(range.groups?.end);
			if (Number.isSafeInteger(start) && Number.isSafeInteger(end) && start > 0 && end >= start) {
				return { path: file, kind: "sed", start, end };
			}
		}
	}
	if (tool === "rg" || tool === "grep") {
		const flags = args.slice(0, -2);
		if (
			flags.includes("-n") &&
			flags.every((flag) => ["-n", "-i", "-F", "-E", "--"].includes(flag))
		) {
			return { path: file, kind: "search" };
		}
	}
	return undefined;
}

/** Verify returned text against the pinned file before granting the exact displayed extents. */
export function bashReadRanges(root: string, read: BashRead, output: string) {
	const source = readFileSync(path.resolve(root, read.path), "utf8");
	if (source.includes("\0")) {
		throw new Error("binary file");
	}
	const lines = source.split("\n");
	const physical = source.endsWith("\n") ? lines.slice(0, -1) : lines;
	if (source === "") {
		physical.length = 0;
	}
	const last = lines.length;
	if (read.kind === "search") {
		const hits = output === "" ? [] : output.replace(/\n$/u, "").split("\n");
		let previous = 0;
		const ranges = hits.map((hit) => {
			const match = /^(?<line>\d+):(?<text>.*)$/u.exec(hit);
			const line = Number(match?.groups?.line);
			if (
				!Number.isSafeInteger(line) ||
				line <= previous ||
				lines[line - 1] !== match?.groups?.text
			) {
				throw new Error("search output does not match the pinned file");
			}
			previous = line;
			return { start: line, end: line };
		});
		return { last, ranges };
	}
	const start =
		read.kind === "tail" ? Math.max(1, physical.length - (read.count ?? 0) + 1) : (read.start ?? 1);
	const end = Math.min(
		physical.length,
		read.kind === "head" ? (read.count ?? 0) : (read.end ?? physical.length),
	);
	const selected = physical.slice(start - 1, end);
	let numbered = 0;
	const rendered =
		read.kind === "nl"
			? selected.map((line) => {
					if (line === "") {
						return "       ";
					}
					numbered += 1;
					return `${String(numbered).padStart(6)}\t${line}`;
				})
			: selected;
	const expected =
		rendered.join("\n") +
		(selected.length > 0 && (end < physical.length || source.endsWith("\n") || read.kind === "nl")
			? "\n"
			: "");
	if (output !== expected && !(expected === "" && output === "(no output)")) {
		throw new Error("read output does not match the pinned file");
	}
	return { last, ranges: [{ start, end: end === physical.length ? last : end }] };
}
