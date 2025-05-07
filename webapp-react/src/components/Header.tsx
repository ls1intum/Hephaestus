import { Link, useNavigate } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Hammer } from "lucide-react";

// Define a proper type for the user
interface User {
  username: string;
  name: string;
  roles?: string[];
}

interface HeaderProps {
  onSignIn?: () => void;
  isSigningIn?: boolean;
  version?: string;
  // Add proper props for user data instead of using internal state
  isSignedIn?: boolean;
  user?: User | null;
}

export default function Header({ 
  onSignIn, 
  isSigningIn = false, 
  version = "v0.6.1",
  isSignedIn = false,
  user = null 
}: HeaderProps = {}) {
  const navigate = useNavigate();
  const handleSignIn = onSignIn || (() => {
    const authUrl = `https://github.com/login/oauth/authorize?client_id=your_client_id&redirect_uri=${encodeURIComponent(window.location.origin)}`;
    window.location.href = authUrl;
  });

  const navigateToUserActivity = (username: string) => {
    navigate({ to: '/user/$username/activity', params: { username } });
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
        
        {isSignedIn && user && (
          <div className="hidden md:flex gap-2">
            <Button variant="link" asChild>
              <Link to="/workspace">Workspace</Link>
            </Button>
            <Button variant="link" onClick={() => navigateToUserActivity(user.username)}>
              Activity
            </Button>
            <Button variant="link" asChild>
              <Link to="/teams">Teams</Link>
            </Button>
          </div>
        )}
      </div>
      
      <div className="flex items-center gap-2">
        {!isSignedIn && (
          <Button onClick={handleSignIn} disabled={isSigningIn}>
            {isSigningIn ? "Signing in..." : "Sign In"}
          </Button>
        )}
      </div>
    </header>
  );
}
