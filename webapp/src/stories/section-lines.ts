import { expect } from "storybook/test";

const LAYOUT_SLACK_PX = 1;
/** The room above and below every line, `gap-8` and `pt-8` in `SectionStack`. */
const SECTION_ROOM_PX = 32;

/**
 * Asserts a record sheet's `SectionStack`: `lines` thin lines, one between each two of its children
 * and none above the first, with 32 px above and below every line. A child that frames itself (a
 * card, an alert) would lend the stack its frame, so every child after the first draws its top edge
 * only. Pass the drawer panel or the story's `canvasElement`; it must draw exactly one stack.
 */
export async function expectSectionLines(root: HTMLElement, lines: number) {
	const stacks = root.querySelectorAll<HTMLElement>('[data-slot="section-stack"]');
	await expect(stacks).toHaveLength(1);
	const [stack] = stacks;
	if (!stack) {
		return;
	}
	const children = [...stack.children].filter(
		(child): child is HTMLElement => child instanceof HTMLElement,
	);
	await expect(children).toHaveLength(lines + 1);
	await expect(Number.parseFloat(getComputedStyle(stack).rowGap)).toBe(SECTION_ROOM_PX);
	for (const [index, child] of children.entries()) {
		const style = getComputedStyle(child);
		const previous = children[index - 1];
		if (previous === undefined) {
			await expect(style.borderTopWidth).toBe("0px");
			continue;
		}
		await expect(style.borderTopWidth).toBe("1px");
		await expect(style.borderTopStyle).toBe("solid");
		await expect([style.borderRightWidth, style.borderBottomWidth, style.borderLeftWidth]).toEqual([
			"0px",
			"0px",
			"0px",
		]);
		const above = child.getBoundingClientRect().top - previous.getBoundingClientRect().bottom;
		await expect(Math.abs(above - SECTION_ROOM_PX)).toBeLessThanOrEqual(LAYOUT_SLACK_PX);
		await expect(Number.parseFloat(style.paddingTop)).toBe(SECTION_ROOM_PX);
	}
}

/** The size of a section heading on a practice surface and a record sheet, `Section size="lg"`. */
const SECTION_HEADING = { fontSize: "20px", fontWeight: "600" } as const;

function stackSections(root: HTMLElement): HTMLElement[] {
	const stack = root.querySelector<HTMLElement>('[data-slot="section-stack"]');
	return stack === null
		? []
		: [...stack.children].filter((child): child is HTMLElement => child instanceof HTMLElement);
}

/**
 * Asserts that every child of the stack is a section headed at `level`, at the one section size,
 * and that its subsections are a step below it at the subsection size, 14 px: a `Section size="sm"`
 * at 500, or a block's label (`LabelledBlock`, `SectionLabel`) at 600.
 */
export async function expectSectionHeadings(root: HTMLElement, level: 2 | 3 = 3) {
	const sections = stackSections(root);
	await expect(sections.length).toBeGreaterThan(0);
	for (const section of sections) {
		await expect(section.tagName).toBe("SECTION");
		const heading = section.querySelector<HTMLElement>(
			`#${CSS.escape(String(section.getAttribute("aria-labelledby")))}`,
		);
		await expect(heading?.tagName).toBe(`H${level}`);
		if (heading === null) {
			continue;
		}
		const style = getComputedStyle(heading);
		await expect({ fontSize: style.fontSize, fontWeight: style.fontWeight }).toEqual(
			SECTION_HEADING,
		);
		for (const sub of section.querySelectorAll<HTMLElement>(":scope section[aria-labelledby]")) {
			const subHeading = sub.querySelector<HTMLElement>(
				`#${CSS.escape(String(sub.getAttribute("aria-labelledby")))}`,
			);
			if (subHeading === null) {
				continue;
			}
			await expect(subHeading.tagName).toBe(`H${level + 1}`);
			const subStyle = getComputedStyle(subHeading);
			await expect({ fontSize: subStyle.fontSize, fontWeight: subStyle.fontWeight }).toEqual({
				fontSize: "14px",
				fontWeight: subHeading.dataset.slot === "section-label" ? "600" : "500",
			});
		}
	}
}

interface FoldUser {
	click: (element: Element) => Promise<void>;
	keyboard: (text: string) => Promise<void>;
}

/**
 * Folds every section of the stack by its heading button and opens it again from the keyboard:
 * open at first, `aria-expanded` and the hidden content follow each press, and the heading stays
 * a heading with the button inside it. Leaves every section open, as it found them.
 */
export async function expectSectionsFold(root: HTMLElement, user: FoldUser) {
	const sections = stackSections(root);
	await expect(sections.length).toBeGreaterThan(0);
	for (const section of sections) {
		const heading = section.querySelector(
			`#${CSS.escape(String(section.getAttribute("aria-labelledby")))}`,
		);
		const button = heading?.querySelector("button[aria-expanded]");
		await expect(button).toBeInstanceOf(HTMLButtonElement);
		if (!(button instanceof HTMLButtonElement)) {
			continue;
		}
		const content = section.querySelector(
			`#${CSS.escape(String(button.getAttribute("aria-controls")))}`,
		);
		await expect(button).toHaveAttribute("aria-expanded", "true");
		await expect(content).not.toHaveAttribute("hidden");
		await user.click(button);
		await expect(button).toHaveAttribute("aria-expanded", "false");
		await expect(content).toHaveAttribute("hidden");
		button.focus();
		await user.keyboard("{Enter}");
		await expect(button).toHaveAttribute("aria-expanded", "true");
		await expect(content).not.toHaveAttribute("hidden");
	}
}
