import { Link } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Hammer, User, LogOut, Settings, Menu } from "lucide-react";
import { 
  Avatar, 
  AvatarFallback, 
  AvatarImage 
} from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ModeToggle } from "@/components/core/ModeToggle";
import RequestFeature from "@/components/core/RequestFeature";
import AIMentor from "@/components/mentor/AIMentor";

export interface HeaderProps {
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
  /** Whether user has admin access */
  showAdmin: boolean;
  /** Whether user has mentor access */
  showMentor: boolean;
  /** Function to call on login button click */
  onLogin: () => void;
  /** Function to call on logout button click */
  onLogout: () => void;
}

export default function Header({
  version,
  isAuthenticated,
  isLoading,
  name,
  username,
  showAdmin,
  showMentor,
  onLogin,
  onLogout
}: HeaderProps) {
  return (
    <header className="container flex items-center justify-between pt-4 gap-2">
      <div className="flex gap-4 items-center flex-1">
        <div className="flex items-center gap-2">
          <Link to="/" search={{}} className="flex gap-2 items-center hover:text-muted-foreground">
            <Hammer className="text-2xl sm:text-3xl" />
            <span className="hidden sm:inline-block text-xl font-semibold">Hephaestus</span>
          </Link>
          <span className="text-xs font-semibold mt-1 text-muted-foreground">{version}</span>
        </div>
        
        {/* Desktop navigation links */}
        {isAuthenticated && (
          <div className="hidden md:flex gap-4">
            {showAdmin && (
              <Button asChild variant="link" size="none">
                <Link to="/workspace" search={{}}>Workspace</Link>
              </Button>
            )}
            <Button asChild variant="link" size="none">
              <Link to="/best-practices" search={{}}>Best practices</Link>
            </Button>
            <Button asChild variant="link" size="none">
              <Link to="/teams" search={{}}>Teams</Link>
            </Button>
          </div>
        )}
        
        {/* Mobile navigation menu */}
        {isAuthenticated && (
          <div className="md:hidden">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="icon">
                  <Menu />
                  <span className="sr-only">Navigation Menu</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                <DropdownMenuLabel>Navigation</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {showAdmin && (
                  <DropdownMenuItem asChild>
                    <Link to="/workspace" search={{}}>Workspace</Link>
                  </DropdownMenuItem>
                )}
                <DropdownMenuItem asChild>
                  <Link to="/best-practices" search={{}}>Best practices</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link to="/teams" search={{}}>Teams</Link>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </div>
      
      {showMentor && (
        <>
          {/* Desktop AI Mentor component */}
          <div className="hidden sm:block">
            <AIMentor iconOnly={false} />
          </div>
          
          {/* Mobile AI Mentor component */}
          <div className="sm:hidden">
            <AIMentor iconOnly={true} />
          </div>
        </>
      )}
      
      {/* Desktop RequestFeature component */}
      <div className="hidden sm:block">
        <RequestFeature iconOnly={false} />
      </div>
      
      {/* Mobile RequestFeature component */}
      <div className="sm:hidden">
        <RequestFeature iconOnly={true} />
      </div>
      
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
                    <AvatarImage src={`https://github.com/${username}.png`} alt={`${username}'s avatar`} />
                    <AvatarFallback>{username?.slice(0, 2)?.toUpperCase() || '?'}</AvatarFallback>
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
                    <Link to="/user/$username" search={{}} params={{ username: username ?? '' }}> 
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
    </header>
  );
}
