import { Link, useMatchRoute } from "@tanstack/react-router";
import {
	BookUser,
	Bot,
	ChevronRight,
	CircleDollarSign,
	ClipboardCheck,
	Map as MapIcon,
	MessageSquareText,
	PlugZapIcon,
	ScrollText,
	Settings2,
	SlidersHorizontal,
	Trophy,
	Users,
} from "lucide-react";
import { useState } from "react";
import { ADMIN_NAV_LABELS } from "@/components/core/sidebar/admin-nav-labels";
import { GithubIcon, GitlabIcon, OutlineIcon, SlackIcon } from "@/components/icons/brand";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuAction,
	SidebarMenuButton,
	SidebarMenuItem,
	SidebarMenuSub,
	SidebarMenuSubButton,
	SidebarMenuSubItem,
} from "@/components/ui/sidebar";

export interface NavAdminProps {
	workspaceSlug: string;
	achievementsEnabled: boolean;
	scmProviderType?: "GITHUB" | "GITLAB";
}

export function NavAdmin({
	workspaceSlug,
	achievementsEnabled,
	scmProviderType = "GITHUB",
}: NavAdminProps) {
	const matchRoute = useMatchRoute();

	const onWorkspaceSettings = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/settings", fuzzy: false }),
	);
	const onMembers = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/members", fuzzy: true }));
	const onTeams = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/teams", fuzzy: true }));
	const onAchievements = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/achievements", fuzzy: true }),
	);
	const onAchievementDesigner = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/achievement-designer", fuzzy: true }),
	);
	const onReviewSettings = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/practices/settings", fuzzy: true }),
	);
	const onReviews = Boolean(
		matchRoute({ to: "/w/$workspaceSlug/admin/practices/reviews", fuzzy: true }),
	);
	const onSection = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/practices", fuzzy: true }));

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
	const onModels = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/models", fuzzy: true }));
	const onUsage = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/usage", fuzzy: true }));
	const onAudit = Boolean(matchRoute({ to: "/w/$workspaceSlug/admin/audit", fuzzy: true }));
	const [practicesOpen, setPracticesOpen] = useState(onSection);
	const [integrationsOpen, setIntegrationsOpen] = useState(onIntegrationsSection);
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
						isActive={onMembers}
						render={<Link to="/w/$workspaceSlug/admin/members" params={{ workspaceSlug }} />}
					>
						<BookUser />
						<span>Members</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Teams"
						isActive={onTeams}
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
							isActive={onAchievements}
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
							isActive={onAchievementDesigner}
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
				<Collapsible
					open={onSection || practicesOpen}
					onOpenChange={setPracticesOpen}
					render={<SidebarMenuItem />}
				>
					<SidebarMenuButton
						tooltip="Practices"
						isActive={onSection}
						render={<Link to="/w/$workspaceSlug/admin/practices" params={{ workspaceSlug }} />}
					>
						<ClipboardCheck />
						<span>Practices</span>
					</SidebarMenuButton>
					<CollapsibleTrigger
						render={<SidebarMenuAction aria-label="Toggle practices" className="group" />}
					>
						<ChevronRight className="transition-transform group-aria-expanded:rotate-90" />
					</CollapsibleTrigger>
					<CollapsibleContent>
						<SidebarMenuSub>
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
						</SidebarMenuSub>
					</CollapsibleContent>
				</Collapsible>
				<Collapsible
					open={onIntegrationsSection || integrationsOpen}
					onOpenChange={setIntegrationsOpen}
					render={<SidebarMenuItem />}
				>
					<SidebarMenuButton
						tooltip="Integrations"
						isActive={onIntegrationsSection}
						render={<Link to="/w/$workspaceSlug/admin/integrations" params={{ workspaceSlug }} />}
					>
						<PlugZapIcon />
						<span>Integrations</span>
					</SidebarMenuButton>
					<CollapsibleTrigger
						render={<SidebarMenuAction aria-label="Toggle integrations" className="group" />}
					>
						<ChevronRight className="transition-transform group-aria-expanded:rotate-90" />
					</CollapsibleTrigger>
					<CollapsibleContent>
						<SidebarMenuSub>
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
						isActive={onModels}
						render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
					>
						<Bot />
						<span>{ADMIN_NAV_LABELS.models}</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="What this workspace spent on AI"
						isActive={onUsage}
						render={<Link to="/w/$workspaceSlug/admin/usage" params={{ workspaceSlug }} />}
					>
						<CircleDollarSign />
						<span>{ADMIN_NAV_LABELS.usage}</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Settings changes in this workspace"
						isActive={onAudit}
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
