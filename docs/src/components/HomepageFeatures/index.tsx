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
    title: 'Process-Aware AI Mentoring',
    kicker: 'Heph provides guidance grounded in your repository activity',
    description:
      'Mentoring sessions blend self-regulated learning prompts with real repo events so goals, reflections, and next steps stay actionable.',
    bullets: [
      'SRL-guided weekly reflection',
      'Repo activity context for objective feedback',
      'Goal setting and progress tracking',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Build Better Team Habits',
    kicker: 'Creativity meets technical expertise — just like on the landing page',
    description:
      'Our approach combines craft, collaboration, and quality so teams stay aligned while the platform scales with them.',
    bullets: [
      'Empower engineers with real-world feedback loops',
      'Foster collaboration that strengthens engineering culture',
      'Improve code quality through friendly competition',
    ],
    cta: {label: 'Dive into contributor docs', to: '/contributor/overview'},
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
