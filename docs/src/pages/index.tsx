import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import feedbackPreviewDark from '../../images/readme/landing-feedback-preview-dark.png';
import feedbackPreviewLight from '../../images/readme/landing-feedback-preview-light.png';
import HomepageFeatures from '../components/HomepageFeatures';

import styles from './index.module.css';

const guideLinks = [
  {
    title: 'User guide',
    description: 'Learn how practice feedback works and how to chat with Heph about your work.',
    to: '/user/overview',
  },
  {
    title: 'Contributor guide',
    description: 'Set up local services, follow coding standards, and ship with confidence.',
    to: '/contributor/overview',
  },
  {
    title: 'Admin guide',
    description: 'Install Hephaestus on your own server and operate it.',
    to: '/admin/install',
  },
];

function HomepageHeader() {
  return (
    <header className={styles.heroSection}>
      <div className="container">
        <div className={styles.heroGrid}>
          <div className={styles.heroCopy}>
            <span className={styles.heroKicker}>Practice feedback for software teams</span>
            <Heading as="h1" className={styles.heroTitle}>
              Learn from the work you&apos;re <span className={styles.heroHighlight}>already doing</span>
            </Heading>
            <p className={styles.heroSubtitle}>
              Hephaestus turns project activity already recorded in your team&apos;s tools into feedback on engineering practices. See what worked, what could improve, and what to try next.
            </p>
            <div className={styles.heroActions}>
              <Link className="button button--primary button--lg" to="/user/overview">
                Read the user guide
              </Link>
              <Link className="button button--link button--lg" href="https://hephaestus.aet.cit.tum.de">
                Open Hephaestus
              </Link>
            </div>
          </div>
          <div className={styles.heroVisual}>
            <img
              className={styles.heroVisualLight}
              src={feedbackPreviewLight}
              alt="An example of practice feedback and a question answered by Heph."
            />
            <img
              className={styles.heroVisualDark}
              src={feedbackPreviewDark}
              alt="An example of practice feedback and a question answered by Heph."
            />
            <p>Practice feedback, followed by a conversation with Heph</p>
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
            Learn to use Hephaestus, contribute to the project, or run your own deployment.
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
      description="Hephaestus gives developers practice feedback on software project work, plus an AI mentor for talking through feedback and next steps.">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <QuickstartGuides />
      </main>
    </Layout>
  );
}
