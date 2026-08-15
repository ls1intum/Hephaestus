import { Link, useMatchRoute } from "@tanstack/react-router";
import { NotebookPen, Radar, Sparkles, Trophy, User, Users } from "lucide-react";
import {
	SidebarGroup,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";

export function NavDashboards({
	username,
	workspaceSlug,
	achievementsEnabled,
	leaderboardEnabled,
}: {
	username: string;
	workspaceSlug: string;
	achievementsEnabled: boolean;
	leaderboardEnabled: boolean;
}) {
	const matchRoute = useMatchRoute();
	const onProfile = Boolean(matchRoute({ to: "/w/$workspaceSlug/user/$username", fuzzy: true }));
	const onAchievements = Boolean(matchRoute({ to: "/w/$workspaceSlug/achievements", fuzzy: true }));
	const onLeaderboard = Boolean(matchRoute({ to: "/w/$workspaceSlug", fuzzy: false }));
	const onTeams = Boolean(matchRoute({ to: "/w/$workspaceSlug/teams", fuzzy: true }));
	const onReviews = Boolean(matchRoute({ to: "/w/$workspaceSlug/reviews", fuzzy: true }));
	const onMyFeedback = Boolean(matchRoute({ to: "/w/$workspaceSlug/my-feedback", fuzzy: true }));

	return (
		<SidebarGroup>
			<SidebarGroupLabel>Dashboards</SidebarGroupLabel>
			<SidebarMenu>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Profile"
						isActive={onProfile}
						render={
							<Link
								to="/w/$workspaceSlug/user/$username"
								params={{ username: username ?? "", workspaceSlug }}
							/>
						}
					>
						<User />
						<span>Profile</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				{/* Next to Profile, because it is the same person's page — but its own entry rather than a
				    tab of the public one: the profile is addressed by whose it is, and this is the page
				    only its owner can ever see. Not feature-gated, for the reason given below. */}
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="My feedback"
						isActive={onMyFeedback}
						render={<Link to="/w/$workspaceSlug/my-feedback" params={{ workspaceSlug }} />}
					>
						<NotebookPen />
						<span>My feedback</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				{achievementsEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Achievements"
							isActive={onAchievements}
							render={<Link to="/w/$workspaceSlug/achievements" params={{ workspaceSlug }} />}
						>
							<Sparkles />
							<span>Achievements</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{leaderboardEnabled && (
					<SidebarMenuItem>
						<SidebarMenuButton
							tooltip="Leaderboard"
							isActive={onLeaderboard}
							render={<Link to="/w/$workspaceSlug" params={{ workspaceSlug }} />}
						>
							<Trophy />
							<span>Leaderboard</span>
						</SidebarMenuButton>
					</SidebarMenuItem>
				)}
				{/* Deliberately not feature-gated: with practices off the page says so, which is the answer
				    a developer wondering why nothing was said came for. */}
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Review activity"
						isActive={onReviews}
						render={<Link to="/w/$workspaceSlug/reviews" params={{ workspaceSlug }} />}
					>
						<Radar />
						<span>Review activity</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
				<SidebarMenuItem>
					<SidebarMenuButton
						tooltip="Teams"
						isActive={onTeams}
						render={<Link to="/w/$workspaceSlug/teams" params={{ workspaceSlug }} />}
					>
						<Users />
						<span>Teams</span>
					</SidebarMenuButton>
				</SidebarMenuItem>
			</SidebarMenu>
		</SidebarGroup>
	);
}
