import { TagIcon } from "@primer/octicons-react";
import { Link } from "@tanstack/react-router";
import { Hammer, LogOut, Settings, User } from "lucide-react";
import { GitHubSignInButton } from "@/components/auth/GitHubSignInButton";
import { ModeToggle } from "@/components/core/ModeToggle";
import { SurveyNotificationButton } from "@/components/surveys/survey-notification-button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
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
import {
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from "@/components/ui/tooltip";

export interface HeaderProps {
	/** Sidebar trigger button component */
	sidebarTrigger?: React.ReactNode;
	/** Application version displayed beside logo */
	version: string;
	/** User authentication state */
	isAuthenticated: boolean;
	/** Whether the authentication is currently loading */
	isLoading: boolean;
	/** Name of the authenticated user */
	name?: string;
	/** Username of the authenticated user */
	username?: string;
	/** Active workspace slug for routing */
	workspaceSlug?: string;
	/** Function to call on login button click */
	onLogin: () => void;
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
	isAuthenticated,
	isLoading,
	name,
	username,
	workspaceSlug,
	onLogin,
	onLogout,
}: HeaderProps) {
	const hasWorkspace = Boolean(workspaceSlug);
	const hasUsername = Boolean(username);

	return (
		<header className="flex h-16 shrink-0 items-center gap-2 transition-[width,height] ease-linear group-has-[[data-collapsible=icon]]/sidebar-wrapper:h-12 justify-between">
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
						<Link
							to="/landing"
							className="flex gap-2 items-center hover:text-muted-foreground"
						>
							<Hammer className="text-2xl sm:text-3xl" />
							<span className="text-xl font-semibold">Hephaestus</span>
						</Link>
					)}
					{/* Version badge - clickable for production releases */}
					{version !== "DEV" && version !== "preview" ? (
						<Tooltip>
							<TooltipTrigger asChild>
								<a
									href={`https://github.com/ls1intum/Hephaestus/releases/tag/v${version}`}
									target="_blank"
									rel="noopener noreferrer"
									className="flex items-center gap-1 text-xs font-mono mt-1 text-muted-foreground hover:text-foreground transition-colors"
								>
									<TagIcon size={12} />
									<span>v{version}</span>
								</a>
							</TooltipTrigger>
							<TooltipContent>View release notes</TooltipContent>
						</Tooltip>
					) : (
						<span className="text-xs font-mono mt-1 text-muted-foreground">
							{version}
						</span>
					)}
				</div>
			</div>
			<div className="flex gap-2 px-4">
				<SurveyNotificationButton />
				<ModeToggle />
				<div className="flex items-center gap-2">
					{!isAuthenticated ? (
						<GitHubSignInButton onClick={onLogin} disabled={isLoading}>
							{isLoading ? "Signing in..." : "Sign in"}
						</GitHubSignInButton>
					) : (
						<div className="flex items-center gap-2">
							<DropdownMenu>
								<DropdownMenuTrigger asChild>
									<Button variant="ghost" size="icon" className="rounded-full">
										<Avatar className="hover:brightness-90">
											<AvatarImage
												src={`https://github.com/${username}.png`}
												alt={`${username}'s avatar`}
											/>
											<AvatarFallback>
												{username?.slice(0, 2)?.toUpperCase() || "?"}
											</AvatarFallback>
										</Avatar>
									</Button>
								</DropdownMenuTrigger>
								<DropdownMenuContent className="w-56" align="end" forceMount>
									<DropdownMenuLabel className="font-normal">
										<div className="flex flex-col space-y-1">
											<p className="text-sm font-medium leading-none">{name}</p>
										</div>
									</DropdownMenuLabel>
									<DropdownMenuSeparator />
									<DropdownMenuGroup>
										{hasWorkspace && hasUsername ? (
											<DropdownMenuItem asChild>
												<Link
													to="/w/$workspaceSlug/user/$username"
													params={{
														workspaceSlug: workspaceSlug ?? "",
														username: username ?? "",
													}}
												>
													<User />
													<span>My Profile</span>
												</Link>
											</DropdownMenuItem>
										) : (
											<DropdownMenuItem
												disabled
												title="Join a workspace to view your profile"
											>
												<User />
												<span>My Profile</span>
											</DropdownMenuItem>
										)}
										<DropdownMenuItem asChild>
											<Link to="/settings" search={{}}>
												<Settings />
												<span>Settings</span>
											</Link>
										</DropdownMenuItem>
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
