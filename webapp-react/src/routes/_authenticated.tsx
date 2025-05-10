import { createFileRoute, Outlet } from '@tanstack/react-router';
import { useAuth } from '@/lib/auth/AuthContext';
import { LandingPage } from '@/features/landing/LandingPage';
import { Spinner } from '@/components/ui/spinner';

// This route will be a parent for all routes that require authentication
export const Route = createFileRoute('/_authenticated')({
  component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
  const { isAuthenticated, isLoading, login } = useAuth();

  // Show loading state if still initializing authentication
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8 gap-2 text-muted-foreground">
        <Spinner/> Loading
      </div>
    );
  }

  // Show landing page instead of login for unauthenticated users
  if (!isAuthenticated) {
    return <LandingPage onSignIn={() => login()} />;
  }

  // User is authenticated, render the child routes
  return <Outlet />;
}