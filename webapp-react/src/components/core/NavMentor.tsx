import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";
import { Link } from "@tanstack/react-router";
import { BotMessageSquare } from "lucide-react";
import { Badge } from "../ui/badge";

export function NavMentor() {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>Mentor</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Mentor">
						<Link to="/mentor">
							<BotMessageSquare />
							<span>Mentor</span>
							<Badge variant="outline">Coming Soon!</Badge>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
