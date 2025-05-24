"use client";

import {
	AudioWaveform,
	BookOpen,
	Bot,
	Command,
	Frame,
	GalleryVerticalEnd,
	Map,
	PieChart,
	Settings2,
	SquareTerminal,
} from "lucide-react";
import type * as React from "react";

import {
	Sidebar,
	SidebarContent,
	SidebarFooter,
	SidebarHeader,
	SidebarRail,
} from "@/components/ui/sidebar";
import { NavAdmin } from "./NavAdmin";
import { NavDashboards } from "./NavDashboards";
import { NavFooter } from "./NavFooter";
import { NavMentor } from "./NavMentor";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

const data = {
	workspaces: [
		{
			name: "AET",
			logoUrl: "https://avatars.githubusercontent.com/u/11064260?s=200&v=4",
		},
	],
};

export function AppSidebar({
	...props
}: React.ComponentProps<typeof Sidebar> & {
	username: string;
	isAdmin: boolean;
}) {
	return (
		<Sidebar collapsible="icon" {...props}>
			<SidebarHeader>
				<WorkspaceSwitcher workspaces={data.workspaces} />
			</SidebarHeader>
			<SidebarContent>
				<NavDashboards username={props.username} />
				<NavMentor />
				{props.isAdmin && <NavAdmin />}
			</SidebarContent>
			<SidebarFooter>
				<NavFooter />
			</SidebarFooter>
			<SidebarRail />
		</Sidebar>
	);
}
