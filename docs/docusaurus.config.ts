import type {Config} from '@docusaurus/types';
import {themes as prismThemes} from 'prism-react-renderer';

const config: Config = {
  title: 'Hephaestus',
  tagline: 'Process-aware mentoring for agile software teams',
  favicon: 'img/branding/logo.svg',
  url: 'https://example.com',
  baseUrl: '/',
  organizationName: 'ls1intum',
  projectName: 'Hephaestus',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },
  markdown: {
    mermaid: true,
  },
  themes: ['@docusaurus/theme-mermaid'],
  presets: [
    [
      'classic',
      {
        docs: {
          path: 'docs',
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/ls1intum/Hephaestus/tree/main/docs',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      },
    ],
  ],
  themeConfig: {
    image: 'img/branding/agile_hephaestus.png',
    navbar: {
      title: 'Hephaestus',
      logo: {
        alt: 'Hephaestus logo',
        src: 'img/branding/logo.svg',
        href: '/docs',
        target: '_self',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docs',
          position: 'left',
          label: 'Docs',
        },
        {
          href: 'https://github.com/ls1intum/Hephaestus',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Overview',
              to: '/docs',
            },
            {
              label: 'User Guide',
              to: '/docs/user/leaderboard',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'GitHub Discussions',
              href: 'https://github.com/ls1intum/Hephaestus/discussions',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'GitHub',
              href: 'https://github.com/ls1intum/Hephaestus',
            },
            {
              label: 'Issue Tracker',
              href: 'https://github.com/ls1intum/Hephaestus/issues',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Hephaestus contributors.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  },
};

export default config;
