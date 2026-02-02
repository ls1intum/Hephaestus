import { Link } from "@tanstack/react-router";
import { Bug, Settings, Sparkles } from "lucide-react";
import {
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarSeparator,
} from "@/components/ui/sidebar";

export function NavFooter() {
	return (
		<SidebarMenu>
			<SidebarMenuItem>
				<SidebarMenuButton tooltip="Settings" render={<Link to="/settings" />}>
					<Settings />
					<span>Settings</span>
				</SidebarMenuButton>
			</SidebarMenuItem>
			<SidebarSeparator />
			<SidebarMenuItem>
				<SidebarMenuButton
					tooltip="Report issue"
					render={<a href="https://github.com/ls1intum/Hephaestus/issues/new/choose" />}
				>
					<Bug />
					<span>Report&nbsp;issue</span>
				</SidebarMenuButton>
			</SidebarMenuItem>
			<SidebarMenuItem>
				<SidebarMenuButton
					tooltip="Request a feature"
					className="text-github-upsell-foreground hover:text-github-upsell-foreground hover:bg-github-upsell-foreground/10 dark:hover:bg-github-upsell-foreground/10"
					render={<a href="https://github.com/ls1intum/Hephaestus/discussions/new/choose" />}
				>
					<Sparkles />
					<span>Request&nbsp;a&nbsp;feature</span>
				</SidebarMenuButton>
			</SidebarMenuItem>
		</SidebarMenu>
	);
}
