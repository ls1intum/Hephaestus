import { describe, expect, it } from "vitest";

import {
	figureDescription,
	figureLine,
	figureName,
	hasFigurePlaceholder,
	insertBlock,
	MAX_SVG_BYTES,
	readSvgFile,
	svgProblem,
	withoutFigure,
} from "./practice-guidance-draft";

describe("figureName", () => {
	it("reads a name the server accepts off the file name", () => {
		expect(figureName("Split Order (v2).SVG", [])).toBe("split-order-v2");
		expect(figureName(`${"a".repeat(63)} b.svg`, [])).toBe("a".repeat(63));
	});

	it("falls back to a plain name when the file name has no letters or digits", () => {
		expect(figureName("___.svg", [])).toBe("figure");
	});

	it("numbers a name the guide already has", () => {
		expect(figureName("split-order.svg", ["split-order"])).toBe("split-order-2");
		expect(figureName("split-order.svg", ["split-order", "split-order-2"])).toBe("split-order-3");
	});
});

describe("insertBlock", () => {
	it("puts the block on a paragraph of its own at the cursor", () => {
		expect(insertBlock("First.\n\nSecond.", "![a](figures/a.svg)", 6)).toBe(
			"First.\n\n![a](figures/a.svg)\n\nSecond.",
		);
	});

	it("puts the block after the text at its end, and alone in empty text", () => {
		expect(insertBlock("First.", "![a](figures/a.svg)", 6)).toBe("First.\n\n![a](figures/a.svg)");
		expect(insertBlock("", "![a](figures/a.svg)", 0)).toBe("![a](figures/a.svg)");
	});

	it("adds no more breaks than a paragraph needs", () => {
		expect(insertBlock("First.\n", "![a](figures/a.svg)", 7)).toBe("First.\n\n![a](figures/a.svg)");
	});
});

describe("withoutFigure", () => {
	it("removes every line that shows the figure and leaves the rest", () => {
		const markdown = `Intro.\n\n${figureLine("split")}\n\nMiddle ![Two](figures/split.svg "t") text.\n\n![Other](figures/other.svg)`;
		expect(withoutFigure(markdown, "split")).toBe(
			"Intro.\n\nMiddle  text.\n\n![Other](figures/other.svg)",
		);
	});

	it("does not touch a figure whose name only starts the same", () => {
		expect(withoutFigure("![A](figures/split-order.svg)", "split")).toBe(
			"![A](figures/split-order.svg)",
		);
	});
	it("removes full, collapsed and shortcut reference images while preserving definitions and code", () => {
		const markdown =
			"![One][square]\n\n![square][]\n\n![square]\n\n[square]: figures/split.svg\n\n`![Example][square]`\n\n```md\n![Example](figures/split.svg)\n\n\n```";
		expect(withoutFigure(markdown, "split")).toBe(
			"[square]: figures/split.svg\n\n`![Example][square]`\n\n```md\n![Example](figures/split.svg)\n\n\n```",
		);
		expect(figureDescription(markdown, "split")).toBe("One");
	});

	it("preserves another image and a link that share a reference definition", () => {
		const markdown =
			"Intro ![Removed][Shared] and [kept link][Shared].\n\n![Other](figures/other.svg)\n\n[shared]: figures/split.svg";
		expect(withoutFigure(markdown, "split")).toBe(
			"Intro  and [kept link][Shared].\n\n![Other](figures/other.svg)\n\n[shared]: figures/split.svg",
		);
	});

	it("uses the first definition and leaves escaped image examples alone", () => {
		const markdown =
			"![Shown][square]\n\n\\![Example][square]\n\n[square]: figures/other.svg\n[square]: figures/split.svg";
		expect(withoutFigure(markdown, "split")).toBe(markdown);
	});
	it("preserves same-line inline-code spaces, hard breaks and indentation", () => {
		expect(
			withoutFigure("Before `a  b` ![caption](figures/split.svg)  \n\n    literal  text", "split"),
		).toBe("Before `a  b`   \n\n    literal  text");
	});
});

describe("figureDescription", () => {
	it("reads what the text says the figure shows", () => {
		expect(figureDescription("![Three changes](figures/split.svg)", "split")).toBe("Three changes");
		expect(figureDescription("![](figures/split.svg)", "split")).toBeUndefined();
	});
});

it("finds a figure line whose description was never written", () => {
	expect(hasFigurePlaceholder(figureLine("split"))).toBe(true);
	expect(hasFigurePlaceholder("![Three changes](figures/split.svg)")).toBe(false);
});

it("names what the browser can see is wrong with SVG markup", () => {
	expect(svgProblem("<svg viewBox='0 0 1 1'></svg>")).toBeUndefined();
	expect(svgProblem("<png/>")).toBe("not-svg");
	expect(svgProblem(`<svg>${"é".repeat(33_000)}</svg>`)).toBe("too-large");
});

describe("readSvgFile", () => {
	it("reads the markup of an SVG file", async () => {
		const svg = "<svg viewBox='0 0 1 1'></svg>";
		await expect(readSvgFile(new File([svg], "a.svg"))).resolves.toStrictEqual({ svg });
	});

	it("refuses a file that is not SVG, or too large to save", async () => {
		await expect(readSvgFile(new File(["PNG"], "photo.png"))).resolves.toStrictEqual({
			problem: "“photo.png” is not an SVG file. Choose an SVG file.",
		});
		await expect(
			readSvgFile(new File(["x".repeat(MAX_SVG_BYTES + 1)], "huge.svg")),
		).resolves.toStrictEqual({
			problem: "“huge.svg” is larger than 64 KB. Simplify the drawing, then try again.",
		});
	});
});
