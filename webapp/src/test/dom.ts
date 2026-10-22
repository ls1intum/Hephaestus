export function precedes(earlier: Node, later: Node): boolean {
	// oxlint-disable-next-line no-bitwise -- compareDocumentPosition returns a bitmask
	return (earlier.compareDocumentPosition(later) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
}

/**
 * The rendered size, in CSS pixels, of the largest `font-size` label in the SVG under `picture`:
 * the label's canvas units scaled by how wide the SVG draws against its viewBox.
 */
export function largestLabelSize(picture: Element): number {
	const svg = picture.querySelector("svg");
	if (svg === null) {
		return 0;
	}
	const scale = svg.getBoundingClientRect().width / svg.viewBox.baseVal.width;
	const sizes = [...svg.querySelectorAll("text")].map((text) =>
		Number(text.getAttribute("font-size") ?? 0),
	);
	return Math.max(0, ...sizes) * scale;
}
