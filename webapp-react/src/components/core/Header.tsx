import { ModeToggle } from "@/components/core/ModeToggle";
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
import { Link } from "@tanstack/react-router";
import { Hammer, LogOut, Settings, User } from "lucide-react";

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
	/** Function to call on login button click */
	onLogin: () => void;
	/** Function to call on logout button click */
	onLogout: () => void;
}

export default function Header({
	sidebarTrigger,
	version,
	isAuthenticated,
	isLoading,
	name,
	username,
	onLogin,
	onLogout,
}: HeaderProps) {
	return (
		<header className="flex h-16 shrink-0 items-center gap-2 transition-[width,height] ease-linear group-has-[[data-collapsible=icon]]/sidebar-wrapper:h-12 justify-between">
			<div className="flex items-center gap-2 px-4">
				{sidebarTrigger}
				<div className="flex items-center gap-2">
					<Link
						to="/"
						className="flex gap-2 items-center hover:text-muted-foreground"
					>
						<Hammer className="text-2xl sm:text-3xl" />
						<span className="text-xl font-semibold">Hephaestus</span>
					</Link>
					<span className="text-xs font-semibold mt-1 text-muted-foreground">
						{version}
					</span>
				</div>
			</div>
			<div className="flex gap-2 px-4">
				<ModeToggle />

				<div className="flex items-center gap-2">
					{!isAuthenticated ? (
						<Button onClick={onLogin} disabled={isLoading}>
							{isLoading ? "Loading..." : "Sign In"}
						</Button>
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
										<DropdownMenuItem asChild>
											<Link
												to="/user/$username"
												search={{}}
												params={{ username: username ?? "" }}
											>
												<User />
												<span>My Profile</span>
											</Link>
										</DropdownMenuItem>
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
