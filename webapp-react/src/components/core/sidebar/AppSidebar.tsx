import {
	Sidebar,
	SidebarContent,
	SidebarFooter,
	SidebarHeader,
	SidebarMenuButton,
	SidebarRail,
} from "@/components/ui/sidebar";
import { Link } from "@tanstack/react-router";
import { SquarePen } from "lucide-react";
import type { JSX } from "react";
import { NavAdmin } from "./NavAdmin";
import { NavContextHeader } from "./NavContextHeader";
import { NavDashboards } from "./NavDashboards";
import { NavFooter } from "./NavFooter";
import { NavMentor } from "./NavMentor";
import { NavMentorThreads } from "./NavMentorThreads";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

const data = {
	workspaces: [
		{
			name: "AET",
			logoUrl: "https://avatars.githubusercontent.com/u/11064260?s=200&v=4",
		},
	],
};

export type SidebarContext = "main" | "mentor";

export function AppSidebar({
	...props
}: React.ComponentProps<typeof Sidebar> & {
	username: string;
	isAdmin: boolean;
	hasMentorAccess: boolean;
	context: SidebarContext;
}) {
	let contextHeader: JSX.Element | undefined;
	let sidebarContent: JSX.Element;
	if (props.context === "mentor") {
		contextHeader = (
			<NavContextHeader title="Mentor">
				<SidebarMenuButton asChild>
					<Link to="/mentor">
						<SquarePen />
						New chat
					</Link>
				</SidebarMenuButton>
			</NavContextHeader>
		);
		sidebarContent = <NavMentorThreads />;
	} else {
		contextHeader = undefined;
		sidebarContent = (
			<>
				<NavDashboards username={props.username} />
				{props.hasMentorAccess && <NavMentor />}
				{props.isAdmin && <NavAdmin />}
			</>
		);
	}

	return (
		<Sidebar
			collapsible={props.context === "main" ? "icon" : "offcanvas"}
			{...props}
		>
			<SidebarHeader>
				<WorkspaceSwitcher
					workspaces={data.workspaces}
					activeWorkspace={data.workspaces[0]}
				/>
				{contextHeader}
			</SidebarHeader>
			<SidebarContent>{sidebarContent}</SidebarContent>
			<SidebarFooter>
				<NavFooter />
			</SidebarFooter>
			<SidebarRail />
		</Sidebar>
	);
}
