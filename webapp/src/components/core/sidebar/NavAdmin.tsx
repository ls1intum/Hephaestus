import { Link, useMatchRoute } from "@tanstack/react-router";
import {
	BookUser,
	Bot,
	ChevronRight,
	CircleDollarSign,
	ClipboardCheck,
	LayoutGridIcon,
	ListChecks,
	Map as MapIcon,
	MessageSquareText,
	PlugZapIcon,
	ScrollText,
	Settings2,
	SlidersHorizontal,
	Trophy,
	Users,
} from "lucide-react";
import { ADMIN_NAV_LABELS } from "@/components/core/sidebar/admin-nav-labels";
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
	scmProviderType?: "GITHUB" | "GITLAB";
}

export function NavAdmin({
	workspaceSlug,
	achievementsEnabled = true,
	practicesEnabled = true,
	scmProviderType = "GITHUB",
}: NavAdminProps) {
	const matchRoute = useMatchRoute();

	const onWorkspaceSettings = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/settings", fuzzy: false }),
	);
	const onReviewSettings = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/practices/settings", fuzzy: true }),
	);
	const onReviews = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/practices/reviews", fuzzy: true }),
	);
	const onSection = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/practices", fuzzy: true }));
	const onCatalog = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/practices", fuzzy: false }) ||
			matchRoute({ to: "/w/$workspaceSlug/admin/practices/new", fuzzy: false }) ||
			matchRoute({ to: "/w/$workspaceSlug/admin/practices/$practiceSlug", fuzzy: false }),
	);

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
						tooltip="Workspace settings"
						isActive={onWorkspaceSettings}
						render={<Link to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} />}
					>
						<Settings2 />
						<span>Settings</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Members"
						render={<Link to="/w/$workspaceSlug/admin/members" params={{ workspaceSlug }} />}
					>
						<BookUser />
						<span>Members</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Teams"
						render={<Link to="/w/$workspaceSlug/admin/teams" params={{ workspaceSlug }} />}
					>
						<Users />
						<span>Teams</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Achievements"
							render={<Link to="/w/$workspaceSlug/admin/achievements" params={{ workspaceSlug }} />}
						>
							<Trophy />
							<span>Achievements</span>
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
									isActive={onReviews}
									render={
										<Link
											to="/w/$workspaceSlug/admin/practices/reviews"
											params={{ workspaceSlug }}
										/>
									}
								>
									<MessageSquareText />
									<span>Practice feedback</span>
								</SidebarMenuSubButton>
							</SidebarMenuSubItem>
							{practicesEnabled && (
								<SidebarMenuSubItem>
									<SidebarMenuSubButton
										isActive={onReviewSettings}
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
							)}
						</SidebarMenuSub>
					</CollapsibleContent>
				</Collapsible>
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
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip={ADMIN_NAV_LABELS.models}
						render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
					>
						<Bot />
						<span>{ADMIN_NAV_LABELS.models}</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="What this workspace spent on AI"
						render={<Link to="/w/$workspaceSlug/admin/usage" params={{ workspaceSlug }} />}
					>
						<CircleDollarSign />
						<span>{ADMIN_NAV_LABELS.usage}</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Settings changes in this workspace"
						render={
							<Link to="/w/$workspaceSlug/admin/audit" params={{ workspaceSlug }} search={{}} />
						}
					>
						<ScrollText />
						<span>{ADMIN_NAV_LABELS.audit}</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
