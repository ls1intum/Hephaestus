import { withThemeByClassName, withThemeFromJSXProvider } from "@storybook/addon-themes";
import type { Decorator, Preview } from "@storybook/react";
import {
	createRootRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import React from "react";
import { ThemeProvider } from "../src/integrations/theme";
import "../src/styles.css";

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
			viewports: {
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
	decorators: [
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
