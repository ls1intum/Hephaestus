import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    'intro',
    {
      type: 'category',
      label: 'User Guide',
      collapsed: false,
      items: [
        'user/leaderboard',
        'user/workspace',
        'user/ai-mentor',
        'user/best-practices',
      ],
    },
    {
      type: 'category',
      label: 'Contributor Guide',
      collapsed: false,
      items: [
        'contributor/setup-guide',
        'contributor/getting-started/index',
        'contributor/testing',
        'contributor/release-management',
        {
          type: 'category',
          label: 'Coding & Design',
          collapsed: true,
          link: {
            type: 'doc',
            id: 'contributor/coding-design-guidelines/index',
          },
          items: [
            'contributor/coding-design-guidelines/performance',
            'contributor/coding-design-guidelines/server',
            'contributor/coding-design-guidelines/intelligence-service',
            'contributor/coding-design-guidelines/client',
            'contributor/coding-design-guidelines/database',
          ],
        },
        {
          type: 'category',
          label: 'Database',
          collapsed: true,
          items: [
            'contributor/database/migration',
            'contributor/database/schema',
          ],
        },
        'contributor/mail-notifications',
        'contributor/system-design/index',
      ],
    },
    {
      type: 'category',
      label: 'Administrator Guide',
      collapsed: false,
      items: ['admin/production-setup'],
    },
  ],
};

export default sidebars;
