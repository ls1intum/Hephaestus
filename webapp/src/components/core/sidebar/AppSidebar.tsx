import { Link } from "@tanstack/react-router";
import { SquarePen } from "lucide-react";
import type { ReactNode } from "react";
import type { ChatThreadSummary, WorkspaceListItem } from "@/api/types.gen";
import {
	Sidebar,
	SidebarContent,
	SidebarFooter,
	SidebarGroup,
	SidebarHeader,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarMenuSkeleton,
	SidebarRail,
} from "@/components/ui/sidebar";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { NavAdmin } from "./NavAdmin";
import { NavContextHeader } from "./NavContextHeader";
import { NavDashboards } from "./NavDashboards";
import { NavFooter } from "./NavFooter";
import { NavMentor } from "./NavMentor";
import { NavMentorThreads } from "./NavMentorThreads";
import { NavSuperAdmin } from "./NavSuperAdmin";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

export type SidebarContext = "main" | "mentor" | "admin";

export interface AppSidebarProps extends React.ComponentProps<typeof Sidebar> {
	username: string;
	isAdmin: boolean;
	/** Application-wide super-admin (APP_ADMIN). Gates the cross-workspace Admin nav group. */
	isAppAdmin: boolean;
	hasMentorAccess: boolean;
	context: SidebarContext;
	workspaces: WorkspaceListItem[];
	activeWorkspace?: WorkspaceListItem;
	onWorkspaceChange?: (workspace: WorkspaceListItem) => void;
	onAddWorkspace?: () => void;
	workspacesLoading?: boolean;
	// Optional mentor thread data - using API types directly
	mentorThreads?: ChatThreadSummary[];
	mentorThreadsLoading?: boolean;
	mentorThreadsError?: string;
}

export function AppSidebar({
	username,
	isAdmin,
	isAppAdmin,
	hasMentorAccess,
	context,
	workspaces,
	activeWorkspace,
	onWorkspaceChange,
	onAddWorkspace,
	workspacesLoading = false,
	mentorThreads,
	mentorThreadsLoading,
	mentorThreadsError,
	...props
}: AppSidebarProps) {
	let contextHeader: ReactNode = null;
	let sidebarContent: ReactNode = null;

	if (context === "admin") {
		// Dedicated instance-admin shell: its own back-to-app header + section nav, rendered
		// independent of any workspace. This is what lets a freshly-bootstrapped APP_ADMIN with
		// zero workspaces reach /admin (the entry point is the always-present footer, below).
		sidebarContent = <NavSuperAdmin />;
	} else if (workspacesLoading) {
		sidebarContent = (
			<SidebarGroup>
				<SidebarMenu>
					{Array.from({ length: 5 }).map((_, index) => (
						<SidebarMenuItem key={index}>
							<SidebarMenuSkeleton showIcon />
						</SidebarMenuItem>
					))}
				</SidebarMenu>
			</SidebarGroup>
		);
	} else if (!activeWorkspace) {
		sidebarContent = (
			<div className="group-data-[collapsible=icon]:hidden">
				<NoWorkspace />
			</div>
		);
	} else if (context === "mentor") {
		contextHeader = (
			<NavContextHeader title="Mentor" workspaceSlug={activeWorkspace.workspaceSlug}>
				<SidebarMenuButton
					render={
						<Link
							to="/w/$workspaceSlug/mentor"
							params={{ workspaceSlug: activeWorkspace.workspaceSlug }}
						/>
					}
				>
					<SquarePen />
					New chat
				</SidebarMenuButton>
			</NavContextHeader>
		);
		sidebarContent = (
			<NavMentorThreads
				workspaceSlug={activeWorkspace.workspaceSlug}
				threads={mentorThreads ?? []}
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
					achievementsEnabled={activeWorkspace.achievementsEnabled}
					leaderboardEnabled={activeWorkspace.leaderboardEnabled}
				/>
				{/* Mentor link requires BOTH the user-scoped account_feature flag and the per-workspace toggle. */}
				{hasMentorAccess && activeWorkspace.mentorEnabled && (
					<NavMentor workspaceSlug={activeWorkspace.workspaceSlug} />
				)}
				{isAdmin && (
					<NavAdmin
						workspaceSlug={activeWorkspace.workspaceSlug}
						achievementsEnabled={activeWorkspace.achievementsEnabled}
						practicesEnabled={activeWorkspace.practicesEnabled}
						mentorEnabled={activeWorkspace.mentorEnabled}
					/>
				)}
				{/* Instance-admin lives in its own /admin shell (reached via the footer entry), never in
				    the per-workspace nav. */}
			</>
		);
	}

	return (
		<Sidebar collapsible={context === "main" ? "icon" : "offcanvas"} {...props}>
			<SidebarHeader>
				{/* The workspace switcher is workspace-scoped chrome; the instance-admin shell is not, so
				    it gets its own back-to-app header (in NavSuperAdmin) instead. */}
				{context !== "admin" && (
					<WorkspaceSwitcher
						isLoading={workspacesLoading}
						workspaces={workspaces}
						activeWorkspace={activeWorkspace}
						onWorkspaceChange={onWorkspaceChange}
						onAddWorkspace={onAddWorkspace}
						isAppAdmin={isAppAdmin}
					/>
				)}
				{contextHeader}
			</SidebarHeader>
			<SidebarContent>{sidebarContent}</SidebarContent>
			<SidebarFooter>
				<NavFooter isAppAdmin={isAppAdmin} />
			</SidebarFooter>
			<SidebarRail />
		</Sidebar>
	);
}
