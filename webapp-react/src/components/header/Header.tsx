import { Link } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Hammer, User, LogOut, Settings, MessageSquarePlus } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthContext";
import environment from "@/environment";
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

export default function Header() {
  const { isAuthenticated, isLoading, username, login, logout, hasRole } = useAuth();

  const showAdmin = isAuthenticated && hasRole('admin');
  const showMentor = isAuthenticated && hasRole('mentor_access');

  return (
    <header className="container flex items-center justify-between pt-4 gap-2">
      <div className="flex gap-4 items-center flex-1">
        <div className="flex items-center gap-2">
          <Link to="/" className="flex gap-2 items-center hover:text-muted-foreground">
            <Hammer className="text-2xl sm:text-3xl" />
            <span className="hidden sm:inline-block text-xl font-semibold">Hephaestus</span>
          </Link>
          <span className="text-xs font-semibold mt-1 text-muted-foreground">{environment.version}</span>
        </div>
        
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
      </div>
      
      {showMentor && (
        <>
          <Button variant="ghost" size="icon" className="hidden sm:inline-flex">
            <MessageSquarePlus className="h-5 w-5" />
            <span className="ml-2">AI Mentor</span>
          </Button>
          <Button variant="ghost" size="icon" className="sm:hidden">
            <MessageSquarePlus className="h-5 w-5" />
          </Button>
        </>
      )}
      
      <Button variant="ghost" size="icon" className="hidden sm:inline-flex">
        <MessageSquarePlus className="h-5 w-5" />
        <span className="ml-2">Request Feature</span>
      </Button>
      
      <Button variant="ghost" size="icon" className="sm:hidden">
        <MessageSquarePlus className="h-5 w-5" />
      </Button>
      
      <ModeToggle />
      
      <div className="flex items-center gap-2">
        {!isAuthenticated ? (
          <Button onClick={() => login()} disabled={isLoading}>
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
                    <p className="text-sm font-medium leading-none">{username}</p>
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
                <DropdownMenuItem onClick={() => logout()}>
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
