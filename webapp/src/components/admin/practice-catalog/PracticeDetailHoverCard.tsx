import type { ReactElement } from "react";
import type { Practice } from "@/api/types.gen";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { artifactKindLabel } from "@/lib/artifact-kinds";

export interface PracticeDetailHoverCardProps {
	practice: Practice;
	/**
	 * The practice's own link. It stays the trigger rather than gaining one: a second control beside
	 * the name would put two tab stops on every row of a hundred-row list to reveal prose that is not
	 * worth one.
	 */
	children: ReactElement;
}

/**
 * The prose a practice carries, on the name that already links to it.
 *
 * <p>Both screens that list practices had the same problem and solved it differently, which is how the
 * two came to look like different products. A row is a name and a control; the sentences that say what
 * the practice is for do not fit next to either, so the catalogue dropped them and the autonomy list
 * set them under every row and clamped them to two lines. Neither is right at a hundred rows — one
 * screen cannot be acted on without opening each practice, and the other cannot be scanned.
 *
 * <p>A preview card is the primitive for exactly this: supplementary detail, on the thing it is about.
 * The three ways in are the three a reader has —
 * <ul>
 *   <li><b>Mouse</b>: hover the name.</li>
 *   <li><b>Keyboard</b>: Base UI's preview card opens on focus-visible as well as hover, so tabbing to
 *       the link shows the card. This is not Radix, whose hover card is mouse-only and whose docs
 *       therefore warn it must not carry essential content.</li>
 *   <li><b>Touch</b>: no hover and no focus ring, so the card never opens — the tap follows the link to
 *       the practice, where every sentence here is a field on the form. That is the reason this wraps
 *       the link rather than a help icon: the fallback has to be somewhere the same tap already goes.</li>
 * </ul>
 *
 * <p>Nothing here is load-bearing for the decision the surrounding row is making. The tier, the kind of
 * work and any limitation stay on the row itself, where they are read without a pointer.
 *
 * <p>Renders the link bare when the practice carries no prose. A locally written practice usually will
 * not, and an empty popup that appears on hover is worse than no popup at all.
 */
export function PracticeDetailHoverCard({ practice, children }: PracticeDetailHoverCardProps) {
	const why = practice.whyItMatters?.trim();
	const good = practice.whatGoodLooksLike?.trim();
	if (!why && !good) return children;

	return (
		<HoverCard>
			<HoverCardTrigger render={children} />
			{/* `align="start"`: the card is about the name it hangs off, and a card centred on a name that
			    runs most of the width of the row starts somewhere the eye did not leave. */}
			<HoverCardContent align="start" className="w-80 space-y-2">
				<p className="font-medium">{practice.name}</p>
				{why && <p className="text-muted-foreground">{why}</p>}
				{good && (
					<p className="text-muted-foreground">
						<span className="text-foreground">What good looks like: </span>
						{good}
					</p>
				)}
				<p className="text-muted-foreground text-xs">
					Reviewed on: {artifactKindLabel(practice.artifactKind)}
				</p>
			</HoverCardContent>
		</HoverCard>
	);
}
