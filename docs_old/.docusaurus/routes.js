import React from 'react';
import ComponentCreator from '@docusaurus/ComponentCreator';

export default [
  {
    path: '/__docusaurus/debug',
    component: ComponentCreator('/__docusaurus/debug', '5ff'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/config',
    component: ComponentCreator('/__docusaurus/debug/config', '5ba'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/content',
    component: ComponentCreator('/__docusaurus/debug/content', 'a2b'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/globalData',
    component: ComponentCreator('/__docusaurus/debug/globalData', 'c3c'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/metadata',
    component: ComponentCreator('/__docusaurus/debug/metadata', '156'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/registry',
    component: ComponentCreator('/__docusaurus/debug/registry', '88c'),
    exact: true
  },
  {
    path: '/__docusaurus/debug/routes',
    component: ComponentCreator('/__docusaurus/debug/routes', '000'),
    exact: true
  },
  {
    path: '/docs',
    component: ComponentCreator('/docs', 'f1a'),
    routes: [
      {
        path: '/docs',
        component: ComponentCreator('/docs', '1df'),
        routes: [
          {
            path: '/docs',
            component: ComponentCreator('/docs', '06c'),
            routes: [
              {
                path: '/docs/',
                component: ComponentCreator('/docs/', 'be8'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/admin/production-setup',
                component: ComponentCreator('/docs/admin/production-setup', '148'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/coding-design-guidelines/',
                component: ComponentCreator('/docs/contributor/coding-design-guidelines/', '3bd'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/coding-design-guidelines/client',
                component: ComponentCreator('/docs/contributor/coding-design-guidelines/client', '03b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/coding-design-guidelines/database',
                component: ComponentCreator('/docs/contributor/coding-design-guidelines/database', '41b'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/coding-design-guidelines/intelligence-service',
                component: ComponentCreator('/docs/contributor/coding-design-guidelines/intelligence-service', 'fec'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/coding-design-guidelines/performance',
                component: ComponentCreator('/docs/contributor/coding-design-guidelines/performance', '66c'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/coding-design-guidelines/server',
                component: ComponentCreator('/docs/contributor/coding-design-guidelines/server', '140'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/database/migration',
                component: ComponentCreator('/docs/contributor/database/migration', 'a35'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/database/schema',
                component: ComponentCreator('/docs/contributor/database/schema', '926'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/getting-started/',
                component: ComponentCreator('/docs/contributor/getting-started/', '652'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/mail-notifications',
                component: ComponentCreator('/docs/contributor/mail-notifications', 'b30'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/release-management',
                component: ComponentCreator('/docs/contributor/release-management', 'a4f'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/setup-guide',
                component: ComponentCreator('/docs/contributor/setup-guide', 'dda'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/system-design/',
                component: ComponentCreator('/docs/contributor/system-design/', '18d'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/contributor/testing',
                component: ComponentCreator('/docs/contributor/testing', 'b5f'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/user/ai-mentor',
                component: ComponentCreator('/docs/user/ai-mentor', '788'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/user/best-practices',
                component: ComponentCreator('/docs/user/best-practices', 'dce'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/user/leaderboard',
                component: ComponentCreator('/docs/user/leaderboard', 'a24'),
                exact: true,
                sidebar: "docs"
              },
              {
                path: '/docs/user/workspace',
                component: ComponentCreator('/docs/user/workspace', '62f'),
                exact: true,
                sidebar: "docs"
              }
            ]
          }
        ]
      }
    ]
  },
  {
    path: '*',
    component: ComponentCreator('*'),
  },
];
