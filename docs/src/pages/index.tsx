import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';

const guideLinks = [
  {
    title: 'User Guide',
    description: 'Navigate weekly rituals, mentoring flows, and leaderboard insights.',
    to: '/user/overview',
  },
  {
    title: 'Contributor Guide',
    description: 'Set up local services, follow coding standards, and ship with confidence.',
    to: '/contributor/overview',
  },
  {
    title: 'Admin Guide',
    description: 'Deploy Hephaestus securely and manage workspace configuration.',
    to: '/admin/production-setup',
  },
];

function HomepageHeader() {
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className={styles.heroTitle}>
          Process-Aware Mentoring for Agile Software Teams
        </Heading>
        <p className={styles.heroSubtitle}>
          Onboard faster and learn better habits with an AI mentor grounded in your repo workflow — from
          issues to pull requests and team rituals.
        </p>
        <div className={styles.buttons}>
          <Link className="button button--lg button--primary" to="/user/overview">
            Explore the User Guide
          </Link>
          <Link className="button button--outline button--lg" to="/contributor/overview">
            Build with Hephaestus
          </Link>
          <Link className="button button--link button--lg" href="https://hephaestus.aet.cit.tum.de">
            Open Hephaestus
          </Link>
        </div>
        <p className={styles.heroSignature}>
          Powered by <span>Heph</span>, your AI mentor
        </p>
      </div>
    </header>
  );
}

function QuickstartGuides(): ReactNode {
  return (
    <section className={styles.quickstartSection}>
      <div className="container">
        <div className={styles.quickstartHeader}>
          <Heading as="h2">Pick the guide that matches your role</Heading>
          <p>
            Whether you&apos;re exploring the mentor experience, extending the platform, or running production operations, the
            docs below map directly to the workflows in the Hephaestus webapp.
          </p>
        </div>
        <div className={styles.quickstartGrid}>
          {guideLinks.map((guide) => (
            <Link key={guide.title} className={styles.quickstartCard} to={guide.to}>
              <div>
                <Heading as="h3">{guide.title}</Heading>
                <p>{guide.description}</p>
              </div>
              <span aria-hidden="true">→</span>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Process-Aware Mentoring for Agile Software Teams with AI guidance, gamified reviews, and actionable analytics.">
      <HomepageHeader />
      <main>
        <QuickstartGuides />
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
