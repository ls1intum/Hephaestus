import { withThemeByClassName, withThemeFromJSXProvider } from "@storybook/addon-themes";
import type { Decorator, Preview } from "@storybook/react-vite";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
	createRootRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { isCommonAssetRequest } from "msw";
import { initialize, mswLoader } from "msw-storybook-addon";
import React from "react";
import { ThemeProvider } from "../src/integrations/theme";
import { handlers } from "../src/mocks/handlers";
import "../src/styles.css";

initialize(
	{
		onUnhandledRequest(request, print) {
			if (
				!isCommonAssetRequest(request) &&
				new URL(request.url).origin === window.location.origin
			) {
				print.error();
			}
		},
		quiet: true,
		serviceWorker: { url: "./mockServiceWorker.js" },
	},
	handlers,
);

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

const RouterDecorator: Decorator = (Story) => {
	const rootRoute = createRootRoute({
		component: () => React.createElement(Story),
	});
	const routeTree = rootRoute;
	const router = createRouter({ routeTree });
	return React.createElement(RouterProvider, { router });
};

const injectDocsThemeCSS = () => {
	if (typeof document === "undefined") return;

	const styleId = "storybook-docs-theme";
	let style = document.getElementById(styleId);

	if (!style) {
		style = document.createElement("style");
		style.id = styleId;
		document.head.appendChild(style);
	}

	style.textContent = `
		.docs-story {
			background-color: var(--background) !important;
			color: var(--foreground) !important;
		}
	`;
};

const ThemeDecorator: Decorator = (Story) => {
	React.useEffect(() => {
		injectDocsThemeCSS();
	}, []);

	return React.createElement(Story);
};

const StorybookThemeProvider = ({
	theme,
	children,
}: {
	theme: string;
	children: React.ReactNode;
}) => {
	return React.createElement(
		ThemeProvider,
		{
			key: theme,
			defaultTheme: theme as "light" | "dark",
			storageKey: "storybook-theme",
		},
		children,
	);
};

const preview: Preview = {
	parameters: {
		// Base UI focus guards redirect focus immediately but axe flags their hidden sentinels.
		// https://github.com/mui/base-ui/issues/4668
		a11y: {
			test: "error",
			// Monaco paints its own syntax-highlighting theme, which is vendored and not ours to
			// restyle; axe flags every token span for contrast. Excluded here rather than suppressed
			// story by story, so a story that mounts a code editor does not have to know about it.
			context: { exclude: "[data-base-ui-focus-guard], .monaco-editor" },
		},
		controls: {
			matchers: {
				color: /(background|color)$/i,
				date: /Date$/,
			},
		},
		options: {
			storySort: {
				order: ["Admin", "Core", "Shared"],
			},
		},
		docs: {
			story: {
				inline: true,
			},
		},
		chromatic: {
			viewports: [1440],
			disableSnapshot: false,
		},
		viewport: {
			options: {
				reflow: {
					name: "Reflow (320px)",
					styles: { width: "320px", height: "568px" },
				},
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
		withThemeByClassName({
			themes: {
				light: "light",
				dark: "dark",
			},
			defaultTheme: "light",
			parentSelector: "html",
		}),
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
