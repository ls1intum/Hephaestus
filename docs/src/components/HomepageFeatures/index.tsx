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
    kicker: 'Identify what\'s working and what\'s not — before it becomes habit',
    description:
      'Analyzes how your team works — across pull requests, reviews, and commits — to catch bad practices before they become habits. Early work gets coaching. Finished work gets rigor.',
    bullets: [
      'Catches bad practices across pull requests, reviews, and commits',
      'Adapts to context — early work gets coaching, finished work gets rigor',
      'You stay in control — accept, dismiss, or challenge any finding',
    ],
    cta: {label: 'See the conceptual model', to: '/contributor/conceptual-model'},
  },
  {
    title: 'Multi-Channel Guidance',
    kicker: 'Coaching at the right time, through the right channel',
    description:
      'When a practice is detected, guidance reaches the right person through the right channel — AI mentoring, notifications, or achievement unlocks. All grounded in real project activity, not assumptions.',
    bullets: [
      'AI mentor (Heph) helps you reflect on your work and plan next steps',
      'Timely notifications reach you where you work — Slack, email, or in-app',
      'Achievements and progression chains celebrate sustained good practices',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Growth Tracking',
    kicker: 'See development trajectories, not just snapshots',
    description:
      'Achievements and a league system track skill development over time. As practices improve, coaching fades — the goal is independence, not dependence on the tool.',
    bullets: [
      'Achievement milestones track your growth from first steps to mastery',
      'A league system gives you a fair, evolving rank based on your contributions',
      'Weekly digests and leaderboards make great work visible to the whole team',
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
