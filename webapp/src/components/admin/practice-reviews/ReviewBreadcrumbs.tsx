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
 * <p>No leaf for the record itself: the heading three lines below is the record's name, and a
 * generic leaf above it — `Delivery / Feedback` over `Feedback for Ada Lovelace` — says it worse.
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
