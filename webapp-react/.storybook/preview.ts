import { withThemeByClassName, withThemeByDataAttribute } from '@storybook/addon-themes';
import type { Decorator, Preview } from '@storybook/react'
import { RouterProvider, createRootRoute, createRouter } from "@tanstack/react-router";
import React from "react";
import { ThemeProvider } from "../src/integrations/theme/ThemeContext";
import '../src/styles.css'

// Create a Tanstack Router decorator
const RouterDecorator: Decorator = (Story) => {
  const rootRoute = createRootRoute({ component: () => React.createElement(Story) });
  const routeTree = rootRoute;
  const router = createRouter({ routeTree });
  return React.createElement(RouterProvider, { router });
};

// Create a Theme Provider decorator - fixed to properly include children prop
const ThemeDecorator: Decorator = (Story, context) => {
  return React.createElement(
    ThemeProvider,
    { 
      defaultTheme: "light", 
      storageKey: "theme", 
      // biome-ignore lint/correctness/noChildrenProp: Fine to pass children here
      children: React.createElement(Story, context.args) 
    }
  );
};

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/
      }
    },
    options: {
      storySort: {
        order: ['core', 'shared'],
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
          name: 'Mobile',
          styles: { width: '375px', height: '667px' }
        },
        tablet: {
          name: 'Tablet',
          styles: { width: '768px', height: '1024px' }
        },
        desktop: {
          name: 'Desktop',
          styles: { width: '1440px', height: '900px' }
        },
        wide: {
          name: 'Wide Desktop',
          styles: { width: '1920px', height: '1080px' }
        }
      }
    }
  },
  decorators: [
    RouterDecorator,
    ThemeDecorator,
    withThemeByClassName({
      themes: {
        light: 'scroll-smooth',
        dark: 'dark bg-background scroll-smooth'
      },
      defaultTheme: 'light'
    }),
    withThemeByDataAttribute({
      attributeName: 'data-color-mode',
      themes: {
        light: 'light',
        dark: 'dark'
      },
      defaultTheme: 'light'
    }),
  ],
};

export default preview;