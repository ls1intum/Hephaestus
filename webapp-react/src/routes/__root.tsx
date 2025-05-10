import { Outlet, createRootRouteWithContext } from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";

import Header from "../components/Header";
import Footer from "../components/Footer";

import TanstackQueryLayout from "../integrations/tanstack-query/layout";
import { AuthProvider } from "../lib/auth/AuthContext";

import type { QueryClient } from "@tanstack/react-query";

interface MyRouterContext {
	queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<MyRouterContext>()({
	component: () => (
		<>
			<AuthProvider>
				<div className="flex flex-col min-h-screen">
					<Header />
					<main className="flex-1">
						<Outlet />
					</main>
					<Footer />
				</div>
				<TanStackRouterDevtools />
				<TanstackQueryLayout />
			</AuthProvider>
		</>
	),
});
