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
    title: 'Practice feedback on real work',
    kicker: 'What was done well, what could be better, and a way to get there',
    description:
      'Hephaestus reviews pull requests and issues against real engineering practices and posts its feedback right where the work happens.',
    bullets: [
      'Works with GitHub and GitLab repositories',
      'Feedback arrives as comments on the pull request or issue',
      'Act on it, push back with a reason, or let it pass',
    ],
    cta: {label: 'See how reviews work', to: '/user/ai-code-review'},
  },
  {
    title: 'Heph, your AI mentor',
    kicker: 'A mentor chat grounded in your repository activity',
    description:
      'Heph knows your recent issues, commits, reviews, and pull requests, so its guidance starts from what you actually did.',
    bullets: [
      'Ask about your changes and get concrete next steps',
      'Reflect on your week with real repo events at hand',
      'Also available in Slack, right where your team talks',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Your workspace, your setup',
    kicker: 'Runs from the tools the team already uses',
    description:
      'Each workspace connects its own repositories and configures what the mentor checks for, plus optional recognition features.',
    bullets: [
      'Connect GitHub and GitLab repositories per workspace',
      'Teams and access sync from your source platform',
      'Optional achievements and a weekly leaderboard, if your admin turns them on',
    ],
    cta: {label: 'Set up a workspace', to: '/user/workspace'},
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
