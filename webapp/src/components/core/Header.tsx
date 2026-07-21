import { TagIcon } from "@primer/octicons-react";
import { Link } from "@tanstack/react-router";
import { Hammer, LogOut, Settings, User } from "lucide-react";
import { SignInButtons } from "@/components/auth/SignInButtons";
import { ModeToggle } from "@/components/core/ModeToggle";
import { SurveyNotificationButton } from "@/components/surveys/survey-notification-button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuGroup,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";
import { type EnvironmentTone, resolveHeaderBadge } from "@/lib/version";

const ENV_DOT: Record<EnvironmentTone, string> = {
	staging: "bg-amber-500 dark:bg-amber-400",
	preview: "bg-violet-500 dark:bg-violet-400",
	local: "bg-muted-foreground/50",
};

export interface HeaderProps {
	/** Sidebar trigger button component */
	sidebarTrigger?: React.ReactNode;
	/** Application version displayed beside logo (used in production) */
	version: string;
	/** Friendly deployment environment name, e.g. "Staging" */
	environmentName: string;
	/** Whether this is the production deployment */
	isProduction: boolean;
	/** User authentication state */
	isAuthenticated: boolean;
	/** Whether the authentication is currently loading */
	isLoading: boolean;
	/** Name of the authenticated user */
	name?: string;
	/** Username of the authenticated user */
	username?: string;
	/** Avatar URL for the authenticated user */
	avatarUrl?: string;
	/** Active workspace slug for routing */
	workspaceSlug?: string;
	/** Function to call on login button click, with optional idpHint */
	onLogin: (idpHint?: string) => void;
	/** Function to call on logout button click */
	onLogout: () => void;
}

/**
 * Header component - fully presentational, receives all data via props.
 * Smart logic (hooks, auth) should be handled by the parent container.
 */
export default function Header({
	sidebarTrigger,
	version,
	environmentName,
	isProduction,
	isAuthenticated,
	isLoading,
	name,
	username,
	workspaceSlug,
	avatarUrl,
	onLogin,
	onLogout,
}: HeaderProps) {
	const hasWorkspace = Boolean(workspaceSlug);
	const hasUsername = Boolean(username);
	const badge = resolveHeaderBadge(version, environmentName, isProduction);

	return (
		<header className="flex h-16 shrink-0 items-center gap-2 transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-12 justify-between">
			<div className="flex items-center gap-2 px-4">
				{sidebarTrigger}
				<div className="flex items-center gap-2">
					{hasWorkspace ? (
						<Link
							to="/w/$workspaceSlug"
							params={{ workspaceSlug: workspaceSlug ?? "" }}
							className="flex gap-2 items-center hover:text-muted-foreground"
						>
							<Hammer className="text-2xl sm:text-3xl" />
							<span className="text-xl font-semibold">Hephaestus</span>
						</Link>
					) : (
						<Link to="/" className="flex gap-2 items-center hover:text-muted-foreground">
							<Hammer className="text-2xl sm:text-3xl" />
							<span className="text-xl font-semibold">Hephaestus</span>
						</Link>
					)}
					{/* Release version in production; environment pill elsewhere. */}
					{badge.kind === "release" ? (
						<Tooltip>
							<TooltipTrigger
								render={
									<a
										href={badge.href}
										target="_blank"
										rel="noopener noreferrer"
										aria-label={badge.ariaLabel}
										className="flex items-center gap-1 text-xs font-mono mt-1 text-muted-foreground hover:text-foreground transition-colors"
									/>
								}
							>
								<TagIcon size={12} />
								<span>{badge.label}</span>
							</TooltipTrigger>
							<TooltipContent>{badge.tooltip}</TooltipContent>
						</Tooltip>
					) : (
						<Tooltip>
							<TooltipTrigger
								render={
									<Badge variant="outline" className="gap-1.5 font-normal text-muted-foreground" />
								}
							>
								<span className={cn("size-1.5 rounded-full", ENV_DOT[badge.tone])} />
								{badge.label}
							</TooltipTrigger>
							<TooltipContent>{badge.label} environment</TooltipContent>
						</Tooltip>
					)}
				</div>
			</div>
			<div className="flex gap-2 px-4">
				<SurveyNotificationButton />
				<ModeToggle />
				<div className="flex items-center gap-2">
					{!isAuthenticated ? (
						<SignInButtons onSignIn={onLogin} disabled={isLoading} header />
					) : (
						<div className="flex items-center gap-2">
							<DropdownMenu>
								<DropdownMenuTrigger
									render={<Button variant="ghost" size="icon" className="rounded-full" />}
								>
									<Avatar className="hover:brightness-90">
										<AvatarImage src={avatarUrl || undefined} alt={`${username}'s avatar`} />
										<AvatarFallback>{getInitials(name, username)}</AvatarFallback>
									</Avatar>
								</DropdownMenuTrigger>
								<DropdownMenuContent className="w-56" align="end">
									<DropdownMenuGroup>
										<DropdownMenuLabel className="font-normal">
											<div className="flex flex-col space-y-1">
												<p className="text-sm font-medium leading-none">{name}</p>
											</div>
										</DropdownMenuLabel>
									</DropdownMenuGroup>
									<DropdownMenuSeparator />
									<DropdownMenuGroup>
										{hasWorkspace && hasUsername ? (
											<Link
												to="/w/$workspaceSlug/user/$username"
												params={{
													workspaceSlug: workspaceSlug ?? "",
													username: username ?? "",
												}}
												className="[&]:no-underline"
											>
												<DropdownMenuItem>
													<User />
													<span>My Profile</span>
												</DropdownMenuItem>
											</Link>
										) : (
											<DropdownMenuItem disabled title="Join a workspace to view your profile">
												<User />
												<span>My Profile</span>
											</DropdownMenuItem>
										)}
										<Link to="/settings" className="[&]:no-underline">
											<DropdownMenuItem>
												<Settings />
												<span>Settings</span>
											</DropdownMenuItem>
										</Link>
									</DropdownMenuGroup>
									<DropdownMenuSeparator />
									<DropdownMenuItem onClick={onLogout}>
										<LogOut />
										<span>Sign Out</span>
									</DropdownMenuItem>
								</DropdownMenuContent>
							</DropdownMenu>
						</div>
					)}
				</div>
			</div>
		</header>
	);
}
