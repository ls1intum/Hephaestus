import { Link } from "@tanstack/react-router";
import type { Practice, ReviewPracticeArea } from "@/api/types.gen";
import { getAreaVisual } from "@/components/admin/practice-catalog/area-visuals";
import { PracticeDetailHoverCard } from "@/components/admin/practice-catalog/PracticeDetailHoverCard";
import { cn } from "@/lib/utils";

export interface ReviewPracticeLinkProps {
	workspaceSlug: string;
	practiceSlug: string;
	practiceName: string;
	/** Absent when the practice is Unassigned, which is a real state and not a missing value. */
	area: ReviewPracticeArea | undefined;
	/**
	 * The full practice record behind `practiceSlug`, which the screen holding this link fetched with
	 * the rest of its data. Optional because nothing the hover card shows is load-bearing — the name
	 * and area are on the row, and the rest is a field on the page the link goes to — so a caller
	 * without the record, like a reader on a touchscreen who gets no card either way, loses nothing.
	 */
	practice?: Practice;
	className?: string;
}

/**
 * The review read models carry a practice's slug and name but not its prose, so the workspace's
 * practice list is the join the hover card needs. That list is fetched once by the screen and handed
 * down rather than asked for per row.
 */
export function ReviewPracticeLink({
	workspaceSlug,
	practiceSlug,
	practiceName,
	area,
	practice,
	className,
}: ReviewPracticeLinkProps) {
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
 * The colour is what an operator scans an area by, so the name is carried by the `title` and the
 * screen-reader text rather than taking visible space on a row that already names the practice, the
 * person, the work and the time.
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
