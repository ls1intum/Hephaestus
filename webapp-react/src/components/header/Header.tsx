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
import { ModeToggle } from "@/components/mode-toggle/ModeToggle";
import RequestFeature from "@/components/request-feature/RequestFeature";
import AIMentor from "@/features/mentor/AIMentor";

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
          <Link to="/" className="flex gap-2 items-center hover:text-muted-foreground">
            <Hammer className="text-2xl sm:text-3xl" />
            <span className="hidden sm:inline-block text-xl font-semibold">Hephaestus</span>
          </Link>
          <span className="text-xs font-semibold mt-1 text-muted-foreground">{version}</span>
        </div>
        
        {/* Desktop navigation links */}
        {showAdmin && (
          <div className="hidden md:flex gap-2">
            <Button asChild variant="link">
              <Link to="/workspace">Workspace</Link>
            </Button>
            <Button asChild variant="link">
              <Link to="/user/$username/activity" params={{ username: username ?? '' }}>Activity</Link>
            </Button>
            <Button asChild variant="link">
              <Link to="/teams">Teams</Link>
            </Button>
          </div>
        )}
        
        {/* Mobile navigation menu */}
        {showAdmin && isAuthenticated && (
          <div className="md:hidden">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="icon" className="h-8 w-8">
                  <Menu className="h-5 w-5" />
                  <span className="sr-only">Navigation Menu</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                <DropdownMenuLabel>Navigation</DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link to="/workspace" className="w-full">Workspace</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link to="/user/$username/activity" params={{ username: username ?? '' }} className="w-full">Activity</Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link to="/teams" className="w-full">Teams</Link>
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
                <Button variant="ghost" className="relative h-8 w-8 rounded-full">
                  <Avatar>
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
                    <Link to="/user/$username" params={{ username: username ?? '' }}> 
                      <User className="mr-2 h-4 w-4" />
                      <span>My Profile</span>
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link to="/settings">
                      <Settings className="mr-2 h-4 w-4" />
                      <span>Settings</span>
                    </Link>
                  </DropdownMenuItem>
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={onLogout}>
                  <LogOut className="mr-2 h-4 w-4" />
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
