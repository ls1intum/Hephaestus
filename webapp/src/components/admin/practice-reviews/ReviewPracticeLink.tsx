import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { listPracticesOptions } from "@/api/@tanstack/react-query.gen";
import type { ReviewPracticeArea } from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { PracticeDetailHoverCard } from "@/components/admin/practice-catalog/PracticeDetailHoverCard";
import { cn } from "@/lib/utils";

export interface ReviewPracticeLinkProps {
	workspaceSlug: string;
	practiceSlug: string;
	practiceName: string;
	/** Absent when the practice is Unassigned, which is a real state and not a missing value. */
	area: ReviewPracticeArea | undefined;
	className?: string;
}

/**
 * The practice an observation is about: its area's colour, its name, a link to its definition, and
 * the prose behind it on hover.
 *
 * <p>This replaces `ReviewPracticeLabel`, which was inert text beside an icon. An operator reading an
 * observation is being told a practice was or was not followed, and had no way from here to find out
 * what the practice asks for — the definition was three navigations away, through a catalogue they
 * would have to search by name. Every review surface named a practice and none of them linked one.
 *
 * <p>The card itself is not new and is not reinvented here: `PracticeDetailHoverCard` is the
 * catalogue's, it opens on focus as well as hover because this kit is Base UI, and it renders the
 * bare link when a practice carries no prose. The gap was that nothing outside the catalogue used
 * it. What this adds is the lookup — the review read models carry a practice's slug and name but not
 * its prose, so the workspace's practice list is the join. One query serves every row on the page;
 * TanStack Query deduplicates it by key.
 *
 * <p>Nothing here is load-bearing (rubric rule 6): the practice's name and area are on the row
 * itself, and everything in the card is a field on the page the link goes to. A reader on a
 * touchscreen, who gets no card, loses nothing — their tap goes to the definition.
 */
export function ReviewPracticeLink({
	workspaceSlug,
	practiceSlug,
	practiceName,
	area,
	className,
}: ReviewPracticeLinkProps) {
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });
	const practice = practicesQuery.data?.find((candidate) => candidate.slug === practiceSlug);
	// `relative` lifts this above the stretched title link of `ReviewRow`, which otherwise covers the
	// whole row and would swallow the click.
	const link = (
		<Link
			to="/w/$workspaceSlug/admin/practices/$practiceSlug"
			params={{ workspaceSlug, practiceSlug }}
			className={cn(
				"relative inline-flex min-w-0 max-w-full items-center gap-1.5 rounded-md hover:underline",
				className,
			)}
		>
			<PracticeAreaMark area={area} />
			<span className="min-w-0 break-words">{practiceName}</span>
		</Link>
	);

	return practice ? (
		<PracticeDetailHoverCard practice={practice}>{link}</PracticeDetailHoverCard>
	) : (
		link
	);
}

/**
 * The area's colour and glyph, at the size of a word.
 *
 * The area's *name* does not appear beside it. It used to, on a second line under the practice, and
 * on a row that already names the practice, the person, the work and the time it is one fact too
 * many — the colour is what an operator scans an area by, and the name is in the card and on the
 * page the link goes to.
 */
function PracticeAreaMark({ area }: { area: ReviewPracticeArea | undefined }) {
	if (!area) return null;
	const { Icon, pill } = getAreaVisual(area.slug, area.name, area.icon, area.color);
	return (
		<span
			title={area.name}
			className={cn("flex size-4 shrink-0 items-center justify-center rounded-sm", pill)}
		>
			<Icon className="size-2.5" aria-hidden />
			<span className="sr-only">{area.name}: </span>
		</span>
	);
}
