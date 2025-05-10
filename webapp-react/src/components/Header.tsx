import { Link, useNavigate } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Hammer, RefreshCw } from "lucide-react";
import { useAuth } from "../lib/auth/AuthContext";

// We'll remove the User interface from here since we don't use it in this component
// and instead use the one from AuthContext if needed in the future

interface HeaderProps {
  version?: string;
}

export default function Header({ 
  version = "v0.6.1",
}: HeaderProps = {}) {
  const navigate = useNavigate();
  const { isAuthenticated, isLoading, username, login, logout, checkAuthState } = useAuth();

  // Remove the duplicate auth callback check that was causing infinite loops
  // The AuthContext component already handles this

  const navigateToUserActivity = (username: string) => {
    navigate({ to: '/user/$username/activity', params: { username } });
  };
  
  // Handle navigation to workspace in a way compatible with TanStack Router
  const navigateToWorkspace = () => {
    // Use window.location for routes that aren't registered yet
    window.location.href = '/_authenticated/workspace';
  };

  return (
    <header className="container flex items-center justify-between pt-4 gap-2">
      <div className="flex gap-4 items-center flex-1">
        <div className="flex items-center gap-2">
          <Link className="flex gap-2 items-center hover:text-muted-foreground" to="/">
            <Hammer className="text-2xl sm:text-3xl" />
            <span className="hidden sm:inline-block text-xl font-semibold">Hephaestus</span>
          </Link>
          <span className="text-xs font-semibold mt-1 text-muted-foreground">{version}</span>
        </div>
        
        {isAuthenticated && username && (
          <div className="hidden md:flex gap-2">
            <Button 
              variant="link" 
              onClick={navigateToWorkspace}
            >
              Workspace
            </Button>
            <Button variant="link" onClick={() => navigateToUserActivity(username)}>
              Activity
            </Button>
            <Button variant="link" asChild>
              <Link to="/teams">Teams</Link>
            </Button>
          </div>
        )}
      </div>
      
      <div className="flex items-center gap-2">
        <Button 
          variant="ghost" 
          size="icon"
          onClick={checkAuthState}
          title="Sync auth state"
          className="text-muted-foreground"
        >
          <RefreshCw size={16} />
        </Button>

        {!isAuthenticated ? (
          <Button onClick={() => login()} disabled={isLoading}>
            {isLoading ? "Loading..." : "Sign In"}
          </Button>
        ) : (
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">{username}</span>
            <Button variant="outline" onClick={() => logout()}>
              Sign Out
            </Button>
          </div>
        )}
      </div>
    </header>
  );
}
