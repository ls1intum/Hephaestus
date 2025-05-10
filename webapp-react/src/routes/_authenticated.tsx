import { createFileRoute, Outlet } from '@tanstack/react-router';
import { useAuth } from '../lib/auth/AuthContext';
import { Login } from '../components/auth/Login';

// This route will be a parent for all routes that require authentication
export const Route = createFileRoute('/_authenticated')({
  component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
  const { isAuthenticated, isLoading } = useAuth();

  // Show loading state if still initializing authentication
  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500" />
      </div>
    );
  }

  // Non-redirected authentication approach - render login component instead of redirecting
  if (!isAuthenticated) {
    return <Login />;
  }

  // User is authenticated, render the child routes
  return <Outlet />;
}