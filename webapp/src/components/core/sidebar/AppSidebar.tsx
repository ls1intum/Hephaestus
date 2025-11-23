import { Link } from "@tanstack/react-router";
import { SquarePen } from "lucide-react";
import type { ReactNode } from "react";
import type { ChatThreadGroup, WorkspaceListItem } from "@/api/types.gen";
import { Empty } from "@/components/ui/empty";
import {
	Sidebar,
	SidebarContent,
	SidebarFooter,
	SidebarHeader,
	SidebarMenuButton,
	SidebarRail,
} from "@/components/ui/sidebar";
import { NavAdmin } from "./NavAdmin";
import { NavContextHeader } from "./NavContextHeader";
import { NavDashboards } from "./NavDashboards";
import { NavFooter } from "./NavFooter";
import { NavMentor } from "./NavMentor";
import { NavMentorThreads } from "./NavMentorThreads";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

export type SidebarContext = "main" | "mentor";

export interface AppSidebarProps extends React.ComponentProps<typeof Sidebar> {
	username: string;
	isAdmin: boolean;
	hasMentorAccess: boolean;
	context: SidebarContext;
	workspaces: WorkspaceListItem[];
	activeWorkspace?: WorkspaceListItem;
	onWorkspaceChange?: (workspace: WorkspaceListItem) => void;
	workspacesLoading?: boolean;
	// Optional mentor thread data - using API types directly
	mentorThreadGroups?: ChatThreadGroup[];
	mentorThreadsLoading?: boolean;
	mentorThreadsError?: string;
}

export function AppSidebar({
	username,
	isAdmin,
	hasMentorAccess,
	context,
	workspaces,
	activeWorkspace,
	onWorkspaceChange,
	workspacesLoading = false,
	mentorThreadGroups,
	mentorThreadsLoading,
	mentorThreadsError,
	...props
}: AppSidebarProps) {
	let contextHeader: ReactNode = null;
	let sidebarContent: ReactNode = null;

	if (workspacesLoading) {
		sidebarContent = (
			<div className="p-4 space-y-3">
				<div className="h-3 w-28 rounded bg-muted animate-pulse" />
				<div className="h-3 w-20 rounded bg-muted animate-pulse" />
				<div className="h-3 w-24 rounded bg-muted animate-pulse" />
			</div>
		);
	} else if (!activeWorkspace) {
		sidebarContent = (
			<div className="p-4">
				<Empty
					title="No workspace"
					description="You’re not a member of any workspace yet. Ask an admin to add you."
				/>
			</div>
		);
	} else if (context === "mentor") {
		contextHeader = (
			<NavContextHeader
				title="Mentor"
				workspaceSlug={activeWorkspace.workspaceSlug}
			>
				<SidebarMenuButton asChild>
					<Link
						to="/w/$workspaceSlug/mentor"
						params={{ workspaceSlug: activeWorkspace.workspaceSlug }}
					>
						<SquarePen />
						New chat
					</Link>
				</SidebarMenuButton>
			</NavContextHeader>
		);
		sidebarContent = (
			<NavMentorThreads
				workspaceSlug={activeWorkspace.workspaceSlug}
				threadGroups={mentorThreadGroups ?? []}
				isLoading={mentorThreadsLoading}
				error={mentorThreadsError}
			/>
		);
	} else {
		sidebarContent = (
			<>
				<NavDashboards
					username={username}
					workspaceSlug={activeWorkspace.workspaceSlug}
				/>
				{hasMentorAccess && (
					<NavMentor workspaceSlug={activeWorkspace.workspaceSlug} />
				)}
				{isAdmin && <NavAdmin workspaceSlug={activeWorkspace.workspaceSlug} />}
			</>
		);
	}

	return (
		<Sidebar collapsible={context === "main" ? "icon" : "offcanvas"} {...props}>
			<SidebarHeader>
				<WorkspaceSwitcher
					isLoading={workspacesLoading}
					workspaces={workspaces}
					activeWorkspace={activeWorkspace}
					onWorkspaceChange={onWorkspaceChange}
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
