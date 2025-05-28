import type { QueryClient } from "@tanstack/react-query";
import {
	Link,
	Outlet,
	createRootRouteWithContext,
	useLocation,
} from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";

import TanstackQueryLayout from "../integrations/tanstack-query/layout";

import { AppSidebar } from "@/components/core/AppSidebar";
import Footer from "@/components/core/Footer";
import Header from "@/components/core/Header";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import environment from "@/environment";
import { type AuthContextType, useAuth } from "@/integrations/auth/AuthContext";
import { useTheme } from "@/integrations/theme";
import { Toaster } from "sonner";

interface MyRouterContext {
	queryClient: QueryClient;
	auth: AuthContextType | undefined;
}

export const Route = createRootRouteWithContext<MyRouterContext>()({
	component: () => {
		const { theme } = useTheme();
		return (
			<>
				<SidebarProvider>
					<AppSidebarContainer />
					<SidebarInset>
						<HeaderContainer />
						<main className="flex-grow p-4">
							<Outlet />
						</main>
						<Footer />
					</SidebarInset>
				</SidebarProvider>
				<Toaster theme={theme} />
				<TanStackRouterDevtools />
				<TanstackQueryLayout />
			</>
		);
	},
	// Add notFoundComponent to handle route not found errors
	notFoundComponent: () => (
		<div className="container py-16 flex flex-col items-center justify-center text-center">
			<h2 className="text-3xl font-bold mb-4">Page Not Found</h2>
			<p className="text-muted-foreground mb-8">
				The page you're looking for doesn't exist or you don't have permission
				to view it.
			</p>
			<Link to="/" className="text-blue-500 hover:underline font-medium">
				Return to Home
			</Link>
		</div>
	),
});

function HeaderContainer() {
	const { pathname } = useLocation();
	const { isAuthenticated, isLoading, username, userProfile, login, logout } =
		useAuth();
	return (
		<Header
			sidebarTrigger={!(pathname === "/landing" || !isAuthenticated) && <SidebarTrigger className="-ml-1" />}
			version={environment.version}
			isAuthenticated={isAuthenticated}
			isLoading={isLoading}
			name={userProfile && `${userProfile.firstName} ${userProfile.lastName}`}
			username={username}
			onLogin={login}
			onLogout={logout}
		/>
	);
}

function AppSidebarContainer() {
	const { pathname } = useLocation();
	const { isAuthenticated, username, hasRole } = useAuth();

	if (pathname === "/landing" || !isAuthenticated || username === undefined) {
		return null;
	}

	return <AppSidebar username={username} isAdmin={hasRole("admin")} />;
}
