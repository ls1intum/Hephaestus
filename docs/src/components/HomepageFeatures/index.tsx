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
    kicker: 'Define practices. Evaluate every contribution against them.',
    description:
      'Admins curate a practice catalog per workspace. An AI agent evaluates each contribution against those practices and produces structured findings with a verdict, severity, evidence, and tailored guidance.',
    bullets: [
      'Workspace-defined practices — your standards, not generic rules',
      'Structured findings: verdict, severity, evidence, and guidance',
      'Contributors mark findings as applied, disputed, or not applicable',
    ],
    cta: {label: 'See the conceptual model', to: '/contributor/conceptual-model'},
  },
  {
    title: 'Adaptive Coaching',
    kicker: 'Guidance adapts to each contributor\'s track record.',
    description:
      'The system tracks each contributor\'s history per practice and instructs the agent to adapt accordingly. New contributors are guided toward concrete examples; repeat issues prompt direct coaching; improving contributors get prompts for reflection.',
    bullets: [
      'Each contributor\'s history with a practice shapes the feedback they get',
      'Findings appear in context, where the work happens',
      'Heph, the AI mentor, supports reflection, goal-setting, and summaries',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Engagement & Recognition',
    kicker: 'Surface contribution activity over time.',
    description:
      'A weekly leaderboard, leagues, and achievements surface contribution activity. Weekly Slack digests highlight standout contributors. Practice-aware recognition is on the roadmap.',
    bullets: [
      'Weekly leaderboard with leagues and achievements',
      'Profile timeline grouped by repository and contribution type',
      'Weekly Slack digests highlight standout contributors',
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
