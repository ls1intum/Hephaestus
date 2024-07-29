import { Preview } from '@storybook/angular';
import { withThemeByClassName } from '@storybook/addon-themes';

const preview: Preview = {
  parameters: {
    actions: { argTypesRegex: '^on[A-Z].*' },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/,
      },
    },
    backgrounds: { disable: true },
  },
  decorators: [
    withThemeByClassName({
      themes: {
        light: '',
        dark: 'dark bg-background',
      },
      defaultTheme: 'light',
    }),
  ],
};

export default preview;
