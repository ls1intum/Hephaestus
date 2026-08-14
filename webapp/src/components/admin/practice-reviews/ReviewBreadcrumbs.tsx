import { Link } from "@tanstack/react-router";
import type { ReactElement } from "react";
import {
	Breadcrumb,
	BreadcrumbItem,
	BreadcrumbLink,
	BreadcrumbList,
	BreadcrumbPage,
	BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";

export interface ReviewBreadcrumbsProps {
	workspaceSlug: string;
	/**
	 * The list this record belongs to, and the trail's last step. Omitted on a screen that hangs off
	 * no list — reviewed work is reached from any of the three.
	 */
	section?: {
		label: string;
		link: ReactElement;
	};
}

/**
 * The trail to a practice-review detail screen. It stops at the section.
 *
 * <p>Every detail screen used to end its trail with a generic leaf that restated the heading three
 * lines below it — `Delivery / Feedback` above `Feedback for Ada Lovelace`, and
 * `Practice reviews / Reviewed work` above a grey `Reviewed work` eyebrow above the work's title.
 * The product owner asked for a sweep of exactly this, and the leaf is what goes: the heading is the
 * record's name, and no breadcrumb can say it better.
 *
 * <p>The section stays a link rather than becoming a `BreadcrumbPage`, because it is the way back to
 * the list with its filters intact — a trail whose last step is dead is a trail that lost the only
 * navigation it had.
 */
export function ReviewBreadcrumbs({ workspaceSlug, section }: ReviewBreadcrumbsProps) {
	return (
		<Breadcrumb>
			<BreadcrumbList>
				<BreadcrumbItem>
					{section ? (
						<BreadcrumbLink
							render={
								<Link
									to="/w/$workspaceSlug/admin/practices/reviews"
									params={{ workspaceSlug }}
									search={{}}
								/>
							}
						>
							Practice reviews
						</BreadcrumbLink>
					) : (
						<BreadcrumbPage>Practice reviews</BreadcrumbPage>
					)}
				</BreadcrumbItem>
				{section && (
					<>
						<BreadcrumbSeparator />
						<BreadcrumbItem>
							<BreadcrumbLink render={section.link} aria-current="page">
								{section.label}
							</BreadcrumbLink>
						</BreadcrumbItem>
					</>
				)}
			</BreadcrumbList>
		</Breadcrumb>
	);
}
