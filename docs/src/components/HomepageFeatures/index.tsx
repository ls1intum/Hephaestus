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
      'AI-powered analysis surfaces anti-patterns in pull requests — missing descriptions, oversized changes, incomplete templates — with lifecycle-aware severity. Drafts get coaching. Ready-to-merge work gets rigor.',
    bullets: [
      'Catches patterns like rubber-stamp reviews and missing context',
      'Adapts severity to PR lifecycle — early work is a coaching moment, not a violation',
      'Contributors close the loop by marking findings as fixed, adjusted, or incorrect',
    ],
    cta: {label: 'See the conceptual model', to: '/contributor/conceptual-model'},
  },
  {
    title: 'Multi-Channel Guidance',
    kicker: 'Coaching at the right time, through the right channel',
    description:
      'Detected practices trigger targeted guidance — from structured AI mentoring sessions to Slack notifications and achievement unlocks. Humans and AI coding agents receive the same feedback grounded in the same activity stream.',
    bullets: [
      'AI mentor (Heph) leads structured reflection tied to real PRs, reviews, and issues',
      'Practice notifications reach contributors via email, Slack, and in-app alerts',
      '60+ achievements with progression chains recognize sustained good practices',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Growth Tracking',
    kicker: 'See development trajectories, not just snapshots',
    description:
      'Achievement progression chains and a league system track skill development over time. As practices improve, coaching intensity fades — matching the Cognitive Apprenticeship model of guided independence.',
    bullets: [
      'Achievement chains from common to mythic track practice milestones',
      'Elo-like league system provides persistent, transparent ranking',
      'Weekly Slack digests and leaderboards make good practices visible to the whole team',
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
