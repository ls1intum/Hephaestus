import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  contributorSidebar: [
    {
      type: 'doc',
      id: 'overview',
      label: 'Overview',
    },
    {
      type: 'category',
      label: 'Development Workflow',
      items: ['local-development', 'testing', 'coding-guidelines', 'dockerless-postgres'],
    },
    {
      type: 'category',
      label: 'Architecture & Data',
      items: ['system-design', 'database-schema', 'database-migration'],
    },
    {
      type: 'category',
      label: 'Operations',
      items: ['release-management', 'mail-notifications'],
    },
  ],
};

export default sidebars;
