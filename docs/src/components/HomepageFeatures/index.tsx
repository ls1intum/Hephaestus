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
    title: 'Code Review Gamification',
    kicker: 'Transform code reviews into learning opportunities',
    description:
      'Friendly competition keeps healthy review habits visible with leaderboards, leagues, and team-wide recognition.',
    bullets: [
      'Weekly leaderboards with GitHub integration',
      'Team competitions across multiple repositories',
      'Structured league system for ongoing engagement',
    ],
    cta: {label: 'See the leaderboard tour', to: '/user/leaderboard'},
  },
  {
    title: 'AI Mentor',
    kicker: 'Heph coaches you based on your actual project activity',
    description:
      'Each session guides you through goal-setting, progress review, and reflection — grounded in real PRs, reviews, and issues, not generic advice.',
    bullets: [
      'Structured weekly reflection tied to your repository activity',
      'Contextual feedback drawn from issues, commits, and reviews',
      'Goal setting with progress tracking across behavioral patterns',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Practice Detection',
    kicker: 'AI-powered behavioral analysis across your project lifecycle',
    description:
      'Hephaestus analyzes PRs, reviews, and issues to detect behavioral patterns — then adjusts feedback based on lifecycle stage. Drafts get supportive coaching. Ready-to-merge work gets rigorous review.',
    bullets: [
      'Detects patterns like rubber-stamp reviews, missing context, and ignored feedback',
      'Lifecycle-aware severity — early work is a coaching moment, not a violation',
      'Scores across four health dimensions: Technical, Process, Social, Cognitive',
    ],
    cta: {label: 'See the conceptual model', to: '/contributor/conceptual-model'},
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
