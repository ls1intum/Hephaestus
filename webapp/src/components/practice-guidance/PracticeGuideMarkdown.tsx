import { cn } from "cn";
import type { PracticeGuide } from "@/api/types.gen";
import {
	UNTRUSTED_MARKDOWN_PROSE,
	UntrustedMarkdown,
	type UntrustedMarkdownImage,
} from "@/components/common/UntrustedMarkdown";

import { guideFigure } from "./practice-svg";
import { PracticeVisual } from "./PracticeVisual";

export interface PracticeGuideMarkdownProps {
	/** The guide: its Markdown and the SVG figures the Markdown shows by name. */
	guide: PracticeGuide;
}

/**
 * A practice guide, in the same restricted Markdown as feedback. The one image form it draws is the
 * guide's own figure, `![alt](figures/<name>.svg)`, as a themed picture named by its alt text, bare
 * on the prose's ground and at its width. Any other image source, and a figure the guide does not
 * carry, draws nothing, so the guide loads nothing from elsewhere.
 */
export function PracticeGuideMarkdown({ guide }: PracticeGuideMarkdownProps) {
	const renderFigure = ({ src, alt }: UntrustedMarkdownImage) => {
		const svg = guideFigure(guide.figures, src);
		return svg === undefined ? null : (
			// 42em is the visual's 672px over its 16px lead, so a figure's text keeps the same size
			// against the guide's smaller body text.
			<figure className="max-w-[42em]">
				<PracticeVisual svg={svg} alt={alt} />
			</figure>
		);
	};
	return (
		// A figure takes the paragraphs' `my-2`: a practice SVG draws to its canvas edges and has no margin.
		<div
			className={cn(UNTRUSTED_MARKDOWN_PROSE, "max-w-2xl text-sm text-pretty prose-figure:my-2")}
		>
			<UntrustedMarkdown renderImage={renderFigure}>{guide.markdown}</UntrustedMarkdown>
		</div>
	);
}
