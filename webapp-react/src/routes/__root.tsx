import { Outlet, createRootRouteWithContext } from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";

import Header from "@/components/header/Header";
import Footer from "@/components/footer/Footer";

import TanstackQueryLayout from "../integrations/tanstack-query/layout";

import type { QueryClient } from "@tanstack/react-query";

interface MyRouterContext {
	queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<MyRouterContext>()({
	component: () => (
		<>
			<div className="flex flex-col min-h-screen">
				<Header />
				<main className="container flex-grow pt-4 pb-8">
					<Outlet />
				</main>
				<Footer />
			</div>
			<TanStackRouterDevtools />
			<TanstackQueryLayout />
		</>
	),
	// Add notFoundComponent to handle route not found errors
	notFoundComponent: () => (
		<div className="container py-16 flex flex-col items-center justify-center text-center">
			<h2 className="text-3xl font-bold mb-4">Page Not Found</h2>
			<p className="text-muted-foreground mb-8">
				The page you're looking for doesn't exist or you don't have permission to view it.
			</p>
			<a href="/" className="text-blue-500 hover:underline font-medium">
				Return to Home
			</a>
		</div>
	)
});
