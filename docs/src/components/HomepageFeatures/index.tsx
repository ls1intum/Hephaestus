import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import Link from '@docusaurus/Link';

import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  description: string;
  cta: {label: string; to: string};
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Your standards, written down.',
    description:
      "Each project keeps a short list of the practices that matter. Hephaestus reads each contribution against that list and writes back beside the change — what's working, what to try next, an example when it helps.",
    cta: {label: 'See the model', to: '/contributor/conceptual-model'},
  },
  {
    title: 'Advice that changes with the person.',
    description:
      "A first-time issue gets a worked example. A repeat issue gets a sharper note. Steady improvement gets a question to think on. Open the mentor when you want to plan, reflect, or talk something through.",
    cta: {label: 'About the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'A quiet place to look back.',
    description:
      "Your profile shows what you've been working on and how your practices are trending. Private to you. There when you want it.",
    cta: {label: 'Your profile', to: '/user/leaderboard'},
  },
];

function Feature({title, description, cta}: FeatureItem) {
  return (
    <div className={clsx('col col--4', styles.featureColumn)}>
      <div className={styles.featureCard}>
        <Heading as="h3">{title}</Heading>
        <p className={styles.description}>{description}</p>
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
