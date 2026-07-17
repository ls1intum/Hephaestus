import { Link } from "@tanstack/react-router";
import { Bug, Settings, ShieldCheck, Sparkles } from "lucide-react";
import {
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarSeparator,
} from "@/components/ui/sidebar";

interface NavFooterProps {
	/** Show the workspace-independent entry into the instance-admin shell (APP_ADMIN only). */
	isAppAdmin?: boolean;
}

export function NavFooter({ isAppAdmin = false }: NavFooterProps) {
	return (
		<SidebarMenu>
			{isAppAdmin && (
				<>
					<SidebarMenuItem>
						<SidebarMenuButton tooltip="Instance admin" render={<Link to="/admin" />}>
							<ShieldCheck />
							<span>Instance&nbsp;admin</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
					<SidebarSeparator />
				</>
			)}
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
					className="text-provider-upsell-foreground hover:text-provider-upsell-foreground hover:bg-provider-upsell-foreground/10 dark:hover:bg-provider-upsell-foreground/10"
					render={<a href="https://github.com/ls1intum/Hephaestus/discussions/new/choose" />}
				>
					<Sparkles />
					<span>Request&nbsp;a&nbsp;feature</span>
				</SidebarMenuButton>
			</SidebarMenuItem>
		</SidebarMenu>
	);
}
