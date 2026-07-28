import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import Link from '@docusaurus/Link';

import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  kicker: string;
  description: string;
  bullets: string[];
  cta: {label: string; to: string};
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Practice feedback',
    kicker: 'Specific feedback on how the work was done',
    description:
      'Hephaestus reviews pull requests, merge requests, and issues against the practices your workspace has chosen.',
    bullets: [
      'Points to evidence in the work',
      'Explains what worked or what could improve',
      'Posts a comment with a practical next step',
    ],
    cta: {label: 'How practice feedback works', to: '/user/ai-code-review'},
  },
  {
    title: 'Chat with Heph',
    kicker: 'Talk through feedback and recent work',
    description:
      'Heph can use your recent issues, commits, reviews, pull or merge requests, and delivered feedback as context.',
    bullets: [
      'Ask a question about a recent change',
      'Reflect on feedback before deciding what to do',
      'Use the web app or a Slack direct message',
    ],
    cta: {label: 'How to chat with Heph', to: '/user/ai-mentor'},
  },
  {
    title: 'Workspace controls',
    kicker: 'Your repositories, practices, and settings',
    description:
      'Workspace admins choose the source repositories, project context, practice catalog, and AI model.',
    bullets: [
      'Connect GitHub or GitLab',
      'Add selected Slack channels and Outline documents as context',
      'Manage members and teams',
    ],
    cta: {label: 'Understand workspaces', to: '/user/workspace'},
  },
];

function Feature({title, kicker, description, bullets, cta}: FeatureItem) {
  return (
    <div className={clsx('col col--4', styles.featureColumn)}>
      <div className={styles.featureCard}>
        <p className={styles.kicker}>{kicker}</p>
        <Heading as="h3">{title}</Heading>
        <p className={styles.description}>{description}</p>
        <ul>
          {bullets.map((bullet) => (
            <li key={bullet}>{bullet}</li>
          ))}
        </ul>
        <Link className={styles.cta} to={cta.to}>
          {cta.label}
        </Link>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className={clsx('row', styles.featureRow)}>
          {FeatureList.map((feature) => (
            <Feature key={feature.title} {...feature} />
          ))}
        </div>
      </div>
    </section>
  );
}
