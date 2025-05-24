import { Bug, Settings, Sparkles } from "lucide-react";

import {
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarSeparator,
} from "@/components/ui/sidebar";
import { Link } from "@tanstack/react-router";

export function NavFooter() {
	return (
		<SidebarMenu>
			<SidebarMenuItem>
				<SidebarMenuButton asChild tooltip="Settings">
					<Link to="/settings">
						<Settings />
						<span>Settings</span>
					</Link>
				</SidebarMenuButton>
			</SidebarMenuItem>
			<SidebarSeparator />
			<SidebarMenuItem>
				<SidebarMenuButton asChild tooltip="Report issue">
					<a
						href="https://github.com/ls1intum/Hephaestus/issues/new/choose"
						aria-describedby="Report issue"
					>
						<Bug />
						<span>Report&nbsp;issue</span>
					</a>
				</SidebarMenuButton>
			</SidebarMenuItem>
			<SidebarMenuItem>
				<SidebarMenuButton
					asChild
					tooltip="Request a feature"
					className="text-github-upsell-foreground hover:text-github-upsell-foreground hover:bg-github-upsell-foreground/10 dark:hover:bg-github-upsell-foreground/10"
				>
					<a
						href="https://github.com/ls1intum/Hephaestus/discussions/new/choose"
						aria-describedby="Request a feature"
					>
						<Sparkles />
						<span>Request&nbsp;a&nbsp;feature</span>
					</a>
				</SidebarMenuButton>
			</SidebarMenuItem>
		</SidebarMenu>
	);
}
