import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";
import { Link } from "@tanstack/react-router";
import { BotMessageSquare, ChevronRight } from "lucide-react";

export function NavMentor() {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>Mentor</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Mentor">
						<Link to="/mentor" className="group/mentor">
							<BotMessageSquare />
							<span>Mentor</span>
							<div className="flex justify-end w-full">
								<ChevronRight className="h-4 w-4 opacity-0 group-hover/mentor:opacity-100 transition-opacity" />
							</div>
						</Link>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
