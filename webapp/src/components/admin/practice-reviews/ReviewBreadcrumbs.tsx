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
	 * no list — reviewed work is reached from any of the sections.
	 */
	section?: {
		label: string;
		link: ReactElement;
	};
}

/**
 * The trail stops at the section: the heading below is the record's own name, and a generic leaf
 * above it says the same thing worse. The section therefore stays a link rather than becoming a
 * `BreadcrumbPage` — it is the way back to the list with its filters intact, and a trail whose last
 * step is dead has lost the only navigation it had.
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
