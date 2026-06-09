import { withThemeByClassName, withThemeFromJSXProvider } from "@storybook/addon-themes";
import type { Decorator, Preview } from "@storybook/react-vite";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
	createRootRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { initialize, mswLoader } from "msw-storybook-addon";
import React from "react";
import { ThemeProvider } from "../src/integrations/theme";
import { handlers } from "../src/mocks/handlers";
import "../src/styles.css";

// Initialize MSW once for the Storybook browser. `onUnhandledRequest: "bypass"`
// lets non-API requests (assets, fonts, the worker script itself) through while
// still mocking the auth endpoints. `serviceWorker.url` resolves the worker under
// Storybook's base path so it loads from `public/mockServiceWorker.js`.
initialize(
	{
		onUnhandledRequest: "bypass",
		quiet: true,
		serviceWorker: { url: "./mockServiceWorker.js" },
	},
	handlers,
);

// A fresh QueryClient per story keeps query caches isolated between stories and
// disables retries/refetches so query-driven components reach a deterministic
// rendered state immediately (rather than spinning or retrying on the mock).
const QueryDecorator: Decorator = (Story) => {
	const queryClient = new QueryClient({
		defaultOptions: {
			queries: {
				retry: false,
				refetchOnWindowFocus: false,
				refetchOnReconnect: false,
				staleTime: Number.POSITIVE_INFINITY,
			},
			mutations: { retry: false },
		},
	});
	return React.createElement(
		QueryClientProvider,
		{ client: queryClient },
		React.createElement(Story),
	);
};

// Create a Tanstack Router decorator
const RouterDecorator: Decorator = (Story) => {
	const rootRoute = createRootRoute({
		component: () => React.createElement(Story),
	});
	const routeTree = rootRoute;
	const router = createRouter({ routeTree });
	return React.createElement(RouterProvider, { router });
};

// CSS injection for docs background theming
const injectDocsThemeCSS = () => {
	if (typeof document !== "undefined") {
		const styleId = "storybook-docs-theme";
		let style = document.getElementById(styleId);

		if (!style) {
			style = document.createElement("style");
			style.id = styleId;
			document.head.appendChild(style);
		}

		style.textContent = `
      /* Ensure docs background respects theme */
  .docs-story {
        background-color: var(--background) !important;
        color: var(--foreground) !important;
      }
      
      /* Theme-aware iframe styling */
  html.dark .docs-story {
        background-color: var(--background) !important;
        color: var(--foreground) !important;
      }
    `;
	}
};

// Theme decorator that handles CSS injection
const ThemeDecorator: Decorator = (Story) => {
	React.useEffect(() => {
		injectDocsThemeCSS();
	}, []);

	return React.createElement(Story);
};

// Custom Theme Provider wrapper that only provides React context
const StorybookThemeProvider = ({
	theme,
	children,
}: {
	theme: string;
	children: React.ReactNode;
}) => {
	// Force re-render when theme changes by using key
	return React.createElement(
		ThemeProvider,
		{
			key: theme, // Force re-mount when theme changes
			defaultTheme: theme as "light" | "dark",
			storageKey: "storybook-theme",
		},
		children,
	);
};

const preview: Preview = {
	parameters: {
		controls: {
			matchers: {
				color: /(background|color)$/i,
				date: /Date$/,
			},
		},
		options: {
			storySort: {
				order: ["core", "shared"],
			},
		},
		// Ensure docs pages also get themed
		docs: {
			story: {
				inline: true,
			},
		},
		// Chromatic configuration for optimal visual testing
		chromatic: {
			// Global viewport coverage for comprehensive testing
			viewports: [375, 768, 1024, 1440, 1920],
			// Disable animations for consistent snapshots
			disableSnapshot: false,
			// Note: modes (themes) must be set per-story due to Chromatic limitation
			// that doesn't support both viewports and modes on the same story
		},
		// Better viewport defaults
		viewport: {
			options: {
				mobile: {
					name: "Mobile",
					styles: { width: "375px", height: "667px" },
				},
				tablet: {
					name: "Tablet",
					styles: { width: "768px", height: "1024px" },
				},
				desktop: {
					name: "Desktop",
					styles: { width: "1440px", height: "900px" },
				},
				wide: {
					name: "Wide Desktop",
					styles: { width: "1920px", height: "1080px" },
				},
			},
		},
	},
	loaders: [mswLoader],
	decorators: [
		QueryDecorator,
		RouterDecorator,
		ThemeDecorator,
		// Apply CSS classes to both canvas and docs
		withThemeByClassName({
			themes: {
				light: "light",
				dark: "dark",
			},
			defaultTheme: "light",
			parentSelector: "html", // Apply to both canvas and docs iframe
		}),
		// Provide React context
		withThemeFromJSXProvider({
			themes: {
				light: { name: "light" },
				dark: { name: "dark" },
			},
			defaultTheme: "light",
			Provider: ({ theme, children }) =>
				React.createElement(StorybookThemeProvider, {
					theme: theme.name,
					children,
				}),
		}),
	],
};

export default preview;
