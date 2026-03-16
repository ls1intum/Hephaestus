import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';

const guideLinks = [
  {
    title: 'User Guide',
    description: 'Use the AI mentor, leaderboards, and weekly digests to improve your practices.',
    to: '/user/overview',
  },
  {
    title: 'Contributor Guide',
    description: 'Set up local development, understand the architecture, and follow coding standards.',
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
    <header className={styles.heroSection}>
      <div className="container">
        <div className={styles.heroCopy}>
          <Heading as="h1" className={styles.heroTitle}>
            How You Build Matters
          </Heading>
          <p className={styles.heroSubtitle}>
            Documentation for the Hephaestus platform — practice analytics, AI mentoring, and code review engagement.
          </p>
          <div className={styles.heroActions}>
            <Link className="button button--primary button--lg" to="/user/overview">
              Browse the User Guide
            </Link>
            <Link className="button button--link button--lg" href="https://hephaestus.aet.cit.tum.de">
              Open Hephaestus
            </Link>
          </div>
        </div>
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
      description="Hephaestus documentation — an open-source platform that analyzes GitHub activity to coach individual contributors on code review and collaboration practices.">
      <HomepageHeader />
      <main>
        <QuickstartGuides />
      </main>
    </Layout>
  );
}
