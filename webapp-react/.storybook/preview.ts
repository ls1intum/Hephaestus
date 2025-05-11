import type { Decorator, Preview } from '@storybook/react'
import { withThemeByClassName, withThemeByDataAttribute } from '@storybook/addon-themes';
import { createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import React from "react";
import { ThemeProvider } from "../src/lib/theme/ThemeContext";
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