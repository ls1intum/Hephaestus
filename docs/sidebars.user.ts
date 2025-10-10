import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  userSidebar: [
    {
      type: 'category',
      label: 'Getting Started',
      collapsible: false,
      items: ['overview', 'getting-started'],
    },
    {
      type: 'category',
      label: 'Core Experiences',
      items: ['leaderboard', 'ai-mentor', 'best-practices'],
    },
  ],
};

export default sidebars;
