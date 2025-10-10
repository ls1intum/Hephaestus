import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  contributorSidebar: [
    {
      type: 'category',
      label: 'Overview',
      collapsible: false,
      items: ['overview'],
    },
    {
      type: 'category',
      label: 'Development',
      items: ['setup', 'architecture', 'documentation'],
    },
  ],
};

export default sidebars;
