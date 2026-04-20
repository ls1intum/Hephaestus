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
      items: ['workspace', 'leaderboard', 'achievements', 'ai-mentor', 'ai-code-review'],
    },
  ],
};

export default sidebars;
