import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import Link from '@docusaurus/Link';

import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  verb: string;
  kicker: string;
  description: string;
  bullets: string[];
  cta: {label: string; to: string};
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Practice catalog + lifecycle detection',
    verb: 'Detect',
    kicker: 'Define the practices that matter. Detect them across the full PR lifecycle.',
    description:
      'Each workspace owns a versioned, inspectable practice catalog. An AI agent runs in a sandboxed Docker container, reads descriptions, commits, review threads, related issues, and contributor history, and produces structured findings — verdict, severity, evidence, recommended action.',
    bullets: [
      'Versioned, inspectable catalog owned by the workspace',
      'Detection reads the whole pull-request lifecycle',
      'Findings carry evidence and an action; contributors confirm, dispute, or dismiss',
    ],
    cta: {label: 'See the conceptual model', to: '/contributor/conceptual-model'},
  },
  {
    title: 'Adaptive guidance, in context and in conversation',
    verb: 'Coach',
    kicker: "Findings adapt to each contributor's history per practice.",
    description:
      'In-context findings appear on the pull request as comments and inline diff notes. The conversational mentor surfaces your activity, asks what is blocking you, and helps you plan.',
    bullets: [
      'In-context channel: PR/MR comments and inline diff notes for the author',
      'Conversational mentor for reflection, goal-setting, and check-ins',
      'Tone shifts with the contributor — examples for newcomers, direct coaching for repeats',
    ],
    cta: {label: 'Work with the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Private surfaces for patterns over time',
    verb: 'Reflect',
    kicker: 'Reflection surfaces show practice patterns, scoped privately.',
    description:
      'Contributors see their own findings and practice history. Facilitators see aggregate practice signals on a coaching dashboard. Workspaces that want a weekly activity recognition surface can enable one.',
    bullets: [
      'Reflection dashboard: your own findings and practice history',
      'Facilitator dashboard: aggregate practice signals that support coaching',
      'Optional weekly activity recognition where the workspace wants it',
    ],
    cta: {label: 'See the leaderboard', to: '/user/leaderboard'},
  },
];

function Feature({title, verb, kicker, description, bullets, cta}: FeatureItem) {
  return (
    <div className={clsx('col col--4', styles.featureColumn)}>
      <div className={styles.featureCard}>
        <span className={styles.kicker}>{verb}</span>
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
