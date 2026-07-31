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
	section?: {
		label: string;
		link: ReactElement;
	};
	current: string;
}

export function ReviewBreadcrumbs({ workspaceSlug, section, current }: ReviewBreadcrumbsProps) {
	return (
		<Breadcrumb>
			<BreadcrumbList>
				<BreadcrumbItem>
					{section ? (
						<BreadcrumbLink render={section.link}>{section.label}</BreadcrumbLink>
					) : (
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
					)}
				</BreadcrumbItem>
				<BreadcrumbSeparator />
				<BreadcrumbItem>
					<BreadcrumbPage>{current}</BreadcrumbPage>
				</BreadcrumbItem>
			</BreadcrumbList>
		</Breadcrumb>
	);
}
