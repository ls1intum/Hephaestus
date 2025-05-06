import type { Preview } from '@storybook/react'
import { withThemeByClassName, withThemeByDataAttribute } from '@storybook/addon-themes';
import '../src/styles.css'

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
  ],
};

export default preview;