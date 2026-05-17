import { addons } from 'storybook/manager-api';

const startCase = (input: string): string =>
  input
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .split(/\s+/)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');

addons.setConfig({
  sidebar: {
    renderLabel: ({ name, type }) => type === 'story' ? name : startCase(name),
  },
});