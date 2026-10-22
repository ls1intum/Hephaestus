import { cn } from "cn";

/**
 * The middle dot between two inline items. Drawn as a small circle rather than the "·" character:
 * a glyph sits on the font's baseline, so next to links and icons centered in a flex row it looks
 * too low. The circle is centered on the line box in a flex row and on the middle of the text inline.
 * The " · " stays in the text, hidden, so copied text and text matchers read the row as before.
 * It takes the color of its row, as the character did.
 *
 * `inline` is for running text, where the dot replaces " · ": margins in em approximate the old
 * spacing at the UI font sizes. Monospace text can override the margins. Flex rows use their own gap.
 */
export function DotSeparator({
	inline = false,
	className,
}: {
	inline?: boolean;
	className?: string;
}) {
	return (
		<span
			aria-hidden="true"
			data-dot-separator
			className={cn(
				"inline-block size-0.5 shrink-0 rounded-full bg-current align-middle",
				inline && "mx-[0.35em]",
				className,
			)}
		>
			<span className="sr-only">{" · "}</span>
		</span>
	);
}
