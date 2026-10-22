import { useId } from "react";

import type { PracticeVisual as PracticeVisualValue } from "@/api/types.gen";
import { hasText } from "@/lib/text";

import { PRACTICE_VISUAL_WIDTH, PracticeVisual } from "./PracticeVisual";

export interface PracticeFigureProps {
	visual: PracticeVisualValue;
	/** What good looks like, set directly under the picture. */
	caption?: string | null;
}

/**
 * A practice's picture with its caption directly under it, named by the caption when it has one.
 * No surface: it sits on the panel's ground, in the text column at the size that keeps its labels at
 * the size of the caption.
 */
export function PracticeFigure({ visual, caption }: PracticeFigureProps) {
	const captionId = useId();
	return (
		<figure
			className="flex flex-col gap-2 text-sm"
			// Named explicitly: not every browser names a figure from its figcaption.
			aria-labelledby={hasText(caption) ? captionId : undefined}
		>
			<PracticeVisual svg={visual.svg} alt={visual.alt} className={PRACTICE_VISUAL_WIDTH} />
			{hasText(caption) && (
				<figcaption id={captionId} className="text-pretty text-muted-foreground">
					{caption}
				</figcaption>
			)}
		</figure>
	);
}
