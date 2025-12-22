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
      items: ['local-development', 'testing', 'coding-guidelines', 'workspace-context', 'dockerless-postgres'],
    },
    {
      type: 'category',
      label: 'Architecture & Data',
      items: ['system-design', 'database-schema', 'database-migration', 'intelligence-service'],
    },
    {
      type: 'category',
      label: 'Operations',
      items: ['release-management', 'mail-notifications'],
    },
    {
      type: 'category',
      label: 'AI Development',
      items: ['ai-agent-workflow'],
    },
  ],
};

export default sidebars;
