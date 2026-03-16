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
    title: 'Practice Detection',
    kicker: 'What gets checked, how to configure it, and how to respond to findings',
    description:
      'LLM-powered checks flag anti-patterns in how contributors collaborate and document their work. Currently analyzes PR descriptions, review comments, and commit patterns. Drafts receive lighter checks than ready-to-merge work.',
    bullets: [
      'Catches anti-patterns in collaboration — across PRs, reviews, and commits',
      'Adapts to lifecycle — drafts get coaching, finished work gets rigor',
      'You stay in control — accept, dismiss, or challenge any finding',
    ],
    cta: {label: 'See the conceptual model', to: '/contributor/conceptual-model'},
  },
  {
    title: 'Multi-Channel Guidance',
    kicker: 'How the AI mentor, notifications, and achievements work',
    description:
      'When a practice is detected, feedback reaches the contributor via AI mentor conversations, Slack notifications, or achievement unlocks — driven by actual project activity.',
    bullets: [
      'AI mentor (Heph) helps you reflect on your work and plan next steps',
      'Notifications via Slack, email, or in-app',
      'Achievements unlock for consistent engagement (e.g., reviewing weekly)',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Growth Tracking',
    kicker: 'Understanding leaderboards, leagues, and weekly digests',
    description:
      'Leaderboards rank contributors by engagement and practice quality. A league system tracks longer-term progression. Weekly Slack digests highlight standout contributors.',
    bullets: [
      'Achievement tiers reflect increasing engagement and consistency',
      'A league system ranks contributors by engagement and practice quality, updated weekly',
      'Weekly digests and leaderboards highlight top contributors to the team',
    ],
    cta: {label: 'See the leaderboard', to: '/user/leaderboard'},
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
