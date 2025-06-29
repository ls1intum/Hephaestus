import { Link } from "@tanstack/react-router";
import { ChevronLeft } from "lucide-react";

import { SidebarMenuButton, SidebarSeparator } from "@/components/ui/sidebar";

/**
 * Header navigation for a context with back button to return to main navigation.
 */
export function NavContextHeader({
	title,
	children,
}: { title: string; children?: React.ReactNode }) {
	return (
		<>
			<SidebarMenuButton asChild>
				<Link to="/" className="font-semibold">
					<ChevronLeft className="h-4 w-4" />
					{title}
				</Link>
			</SidebarMenuButton>
			{children && <SidebarSeparator />}
			{children}
		</>
	);
}
