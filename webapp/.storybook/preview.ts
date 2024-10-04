import { applicationConfig, Preview } from '@storybook/angular';
import { withThemeByClassName, withThemeByDataAttribute } from '@storybook/addon-themes';
import { DocsContainer } from '@storybook/blocks';
import { createElement } from 'react';
import { themes } from '@storybook/core/theming';
import { appConfig } from 'app/app.config';

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/
      }
    },
    backgrounds: { disable: true },
    docs: {
      // Fix for the DocsContainer theme
      container: (props: any) => {
        // Function to get the computed CSS variable value
        const getCSSVariableValue = (variableName: string) => {
          const el = document.createElement('div');
          el.style.display = 'none';
          el.style.setProperty('--dummy', 'initial');
          document.body.appendChild(el);
          const computedStyle = getComputedStyle(el);
          const value = computedStyle.getPropertyValue(variableName);
          document.body.removeChild(el);
          return value.trim();
        };

        // Function to get the HSL value of the --background variable
        const getBackgroundHSL = (theme: 'light' | 'dark') => {
          document.documentElement.className = theme;
          return getCSSVariableValue('--background');
        };

        // Fixing the theme for the DocsContainer
        const el = document.querySelector('html');
        const currentTheme = (el?.dataset['colorMode'] as 'light' | 'dark') || 'light';
        const theme = currentTheme === 'dark' ? themes.dark : themes.light;
        const backgroundHSL = getBackgroundHSL(currentTheme);
        props.theme = {
          ...theme,
          appContentBg: `hsl(${backgroundHSL})`
        };
        el?.setAttribute('data-color-mode', currentTheme);
        el?.setAttribute('data-theme', currentTheme);
        el?.setAttribute('data-light-theme', 'light');
        el?.setAttribute('data-dark-theme', 'dark');
        return createElement(DocsContainer, props);
      }
    }
  },
  decorators: [
    withThemeByClassName({
      themes: {
        light: '',
        dark: 'dark bg-background'
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
    applicationConfig(appConfig)
  ]
};

export default preview;
