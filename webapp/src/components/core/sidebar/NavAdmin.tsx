import { Link, useMatchRoute } from "@tanstack/react-router";
import {
	BookUser,
	Bot,
	ChevronRight,
	ClipboardCheck,
	History,
	LayoutGridIcon,
	ListChecks,
	Map as MapIcon,
	PlayCircle,
	PlugZapIcon,
	Settings2,
	SlidersHorizontal,
	Trophy,
	Users,
} from "lucide-react";
import { GithubIcon, GitlabIcon, OutlineIcon, SlackIcon } from "@/components/icons/brand";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarMenuSub,
	SidebarMenuSubButton,
	SidebarMenuSubItem,
} from "@/components/ui/sidebar";

export interface NavAdminProps {
	workspaceSlug: string;
	achievementsEnabled?: boolean;
	practicesEnabled?: boolean;
	mentorEnabled?: boolean;
	/** Drives the Integrations sub-item's label + icon (GitHub vs GitLab). Defaults to GitHub. */
	scmProviderType?: "GITHUB" | "GITLAB";
}

// "Practices" nests as a collapsible sub-tree because it owns several surfaces (the catalog, review
// settings, and the run log). "Models" is a separate item, shared with the mentor, rather than living
// under Practices. Individual practices are not sidebar entries — there are dozens; the catalog is
// their home and each drills down to its own detail page.
export function NavAdmin({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
	mentorEnabled = false,
	scmProviderType = "GITHUB",
}: NavAdminProps) {
	const modelsVisible = practicesEnabled || mentorEnabled;
	const matchRoute = useMatchRoute();

	const onSettings = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/practices/settings", fuzzy: true }),
	);
	const onRuns = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/practices/runs", fuzzy: true }));
	const onSection = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/practices", fuzzy: true }));
	// Catalog is the section's index; a practice's detail page drills down under it, so anything in the
	// section that isn't Review settings or Runs counts as Catalog.
	const onCatalog = onSection && !onSettings && !onRuns;

	const onIntegrationsScm = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/integrations/scm", fuzzy: true }),
	);
	const onIntegrationsSlack = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/integrations/slack", fuzzy: true }),
	);
	const onIntegrationsOutline = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/integrations/outline", fuzzy: true }),
	);
	const onIntegrationsSection = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/integrations", fuzzy: true }),
	);
	const onIntegrationsOverview =
		onIntegrationsSection && !onIntegrationsScm && !onIntegrationsSlack && !onIntegrationsOutline;
	const ScmIcon = scmProviderType === "GITLAB" ? GitlabIcon : GithubIcon;
	const scmLabel = scmProviderType === "GITLAB" ? "GitLab" : "GitHub";

	return (
		<SidebarGroup>
			<SidebarGroupLabel>Administration</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Manage workspace"
						render={<Link to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} />}
					>
						<Settings2 />
						<span>Manage workspace</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Manage members"
						render={<Link to="/w/$workspaceSlug/admin/members" params={{ workspaceSlug }} />}
					>
						<BookUser />
						<span>Manage members</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Manage teams"
						render={<Link to="/w/$workspaceSlug/admin/teams" params={{ workspaceSlug }} />}
					>
						<Users />
						<span>Manage teams</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Manage achievements"
							render={<Link to="/w/$workspaceSlug/admin/achievements" params={{ workspaceSlug }} />}
						>
							<Trophy />
							<span>Manage achievements</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Achievement designer"
							render={
								<Link
									to="/w/$workspaceSlug/admin/achievement-designer"
									params={{ workspaceSlug }}
								/>
							}
						>
							<MapIcon />
							<span>Achievement designer</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{practicesEnabled && (
					<Collapsible defaultOpen={onSection} render={<SidebarMenuItem />}>
						<CollapsibleTrigger
							render={
								<SidebarMenuButton
									tooltip="Practices"
									isActive={onSection}
									className="group/collapsible"
								>
									<ClipboardCheck />
									<span>Practices</span>
									<ChevronRight className="ml-auto transition-transform group-aria-expanded/collapsible:rotate-90" />
								</SidebarMenuButton>
							}
						/>
						<CollapsibleContent>
							<SidebarMenuSub>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onCatalog}
										render={
											<Link to="/w/$workspaceSlug/admin/practices" params={{ workspaceSlug }} />
										}
									>
										<ListChecks />
										<span>Catalog</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onSettings}
										render={
											<Link
												to="/w/$workspaceSlug/admin/practices/settings"
												params={{ workspaceSlug }}
											/>
										}
									>
										<SlidersHorizontal />
										<span>Review settings</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onRuns}
										render={
											<Link
												to="/w/$workspaceSlug/admin/practices/runs"
												params={{ workspaceSlug }}
											/>
										}
									>
										<PlayCircle />
										<span>Runs</span>
									</SidebarMenuSubButton>
								</SidebarMenuSubItem>
							</SidebarMenuSub>
						</CollapsibleContent>
					</Collapsible>
				)}
				<Collapsible defaultOpen={onIntegrationsSection} render={<SidebarMenuItem />}>
					<CollapsibleTrigger
						render={
							<SidebarMenuButton
								tooltip="Integrations"
								isActive={onIntegrationsSection}
								className="group/collapsible"
							>
								<PlugZapIcon />
								<span>Integrations</span>
								<ChevronRight className="ml-auto transition-transform group-aria-expanded/collapsible:rotate-90" />
							</SidebarMenuButton>
						}
					/>
					<CollapsibleContent>
						<SidebarMenuSub>
							<SidebarMenuSubItem>
								<SidebarMenuSubButton
									isActive={onIntegrationsOverview}
									render={
										<Link to="/w/$workspaceSlug/admin/integrations" params={{ workspaceSlug }} />
									}
								>
									<LayoutGridIcon />
									<span>Overview</span>
								</SidebarMenuSubButton>
							</SidebarMenuSubItem>
							<SidebarMenuSubItem>
								<SidebarMenuSubButton
									isActive={onIntegrationsScm}
									render={
										<Link
											to="/w/$workspaceSlug/admin/integrations/scm"
											params={{ workspaceSlug }}
										/>
									}
								>
									<ScmIcon />
									<span>{scmLabel}</span>
								</SidebarMenuSubButton>
							</SidebarMenuSubItem>
							<SidebarMenuSubItem>
								<SidebarMenuSubButton
									isActive={onIntegrationsSlack}
									render={
										<Link
											to="/w/$workspaceSlug/admin/integrations/slack"
											params={{ workspaceSlug }}
										/>
									}
								>
									<SlackIcon />
									<span>Slack</span>
								</SidebarMenuSubButton>
							</SidebarMenuSubItem>
							<SidebarMenuSubItem>
								<SidebarMenuSubButton
									isActive={onIntegrationsOutline}
									render={
										<Link
											to="/w/$workspaceSlug/admin/integrations/outline"
											params={{ workspaceSlug }}
										/>
									}
								>
									<OutlineIcon />
									<span>Outline</span>
								</SidebarMenuSubButton>
							</SidebarMenuSubItem>
						</SidebarMenuSub>
					</CollapsibleContent>
				</Collapsible>
				{modelsVisible && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Models"
							render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
						>
							<Bot />
							<span>Models</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Setting change history"
						render={<Link to="/w/$workspaceSlug/admin/config-audit" params={{ workspaceSlug }} />}
					>
						<History />
						<span>Configuration changes</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
