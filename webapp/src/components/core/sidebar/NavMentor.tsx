import { Link } from "@tanstack/react-router";
import { ChevronRight, Sparkles } from "lucide-react";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Badge } from "@/components/ui/badge";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

export function NavMentor({ workspaceSlug }: { workspaceSlug: string }) {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>Mentor</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton asChild tooltip="Heph - AI Mentor">
						<Link
							to="/w/$workspaceSlug/mentor"
							params={{ workspaceSlug }}
							className="group/mentor items-center gap-2"
						>
							<MentorIcon
								className="-mx-1.5"
								size={30}
								pad={5}
								strokeWidth={1.5}
							/>
							<span className="flex items-center gap-2">
								Heph
								<Badge variant="outline" className="text-muted-foreground">
									<Sparkles className="h-3.5 w-3.5" /> AI Mentor
								</Badge>
							</span>
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
