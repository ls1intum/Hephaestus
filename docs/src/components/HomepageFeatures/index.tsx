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
      "Each project keeps a short list of the practices that matter. When a contribution comes in, that list and the contribution are read together. A comment appears alongside the existing review — with the evidence behind it and a suggested next move.",
    cta: {label: 'See the model', to: '/contributor/conceptual-model'},
  },
  {
    title: 'A mentor when you want to think out loud.',
    description:
      "Open a conversation when you want to plan, reflect, or talk through what's stuck. The mentor has access to your recent activity.",
    cta: {label: 'About the mentor', to: '/user/ai-mentor'},
  },
  {
    title: 'Self-hostable, and yours to shape.',
    description:
      "Open-source. Run it on your own infrastructure, point it at your own AI model, and define the practices that matter to your project.",
    cta: {label: 'Set it up', to: '/contributor/local-development'},
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
