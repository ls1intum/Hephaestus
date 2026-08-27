import Link from "@docusaurus/Link";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import landingHeroDark from "@site/images/readme/landing-hero-dark.png";
import landingHeroLight from "@site/images/readme/landing-hero-light.png";
import HomepageFeatures from "@site/src/components/HomepageFeatures";
import Heading from "@theme/Heading";
import Layout from "@theme/Layout";
import type { ReactNode } from "react";

import styles from "./index.module.css";

const guideLinks = [
	{
		title: "User guide",
		description: "Learn how practice feedback works and how to chat with Heph about your work.",
		to: "/user/overview",
	},
	{
		title: "Contributor guide",
		description: "Set up local services and open your first pull request.",
		to: "/contributor/overview",
	},
	{
		title: "Admin guide",
		description: "Install Hephaestus on your own server and keep it running.",
		to: "/admin/overview",
	},
];

function HomepageHeader() {
	return (
		<header className={styles.heroSection}>
			<div className="container">
				<div className={styles.heroGrid}>
					<div className={styles.heroCopy}>
						<span className={styles.heroKicker}>Practice feedback for GitHub and GitLab teams</span>
						<Heading as="h1" className={styles.heroTitle}>
							Learn from the work you&apos;re{" "}
							<span className={styles.heroHighlight}>already doing</span>
						</Heading>
						<p className={styles.heroSubtitle}>
							Hephaestus is an open-source AI mentor for software teams. It reads the work you
							already do — issues, pull requests, reviews and the discussion around them — against
							the practices your project cares about, then says what went well, what could be
							better, and a way to get there.
						</p>
						<div className={styles.heroActions}>
							<Link className="button button--primary button--lg" to="/user/overview">
								Read the user guide
							</Link>
							<Link
								className="button button--link button--lg"
								href="https://hephaestus.aet.cit.tum.de"
							>
								Open Hephaestus
							</Link>
						</div>
					</div>
					<div className={styles.heroVisual}>
						<img
							className={styles.heroVisualLight}
							src={landingHeroLight}
							alt="An illustration of one change through a project: issue #412 has no acceptance criteria, the pull request grew to 34 files and picked up an unrelated rename, a reviewer asks a good question, and it merges with that thread unresolved. Hephaestus points back to the issue."
						/>
						<img
							className={styles.heroVisualDark}
							src={landingHeroDark}
							alt="An illustration of one change through a project: issue #412 has no acceptance criteria, the pull request grew to 34 files and picked up an unrelated rename, a reviewer asks a good question, and it merges with that thread unresolved. Hephaestus points back to the issue."
						/>
					</div>
				</div>
			</div>
		</header>
	);
}

function QuickstartGuides(): ReactNode {
	return (
		<section className={styles.quickstartSection}>
			<div className="container">
				<div className={styles.quickstartHeader}>
					<Heading as="h2">Pick the guide that matches your role</Heading>
					<p>Learn to use Hephaestus, contribute to the project, or run your own deployment.</p>
				</div>
				<div className={styles.quickstartGrid}>
					{guideLinks.map((guide) => (
						<Link key={guide.title} className={styles.quickstartCard} to={guide.to}>
							<div>
								<Heading as="h3">{guide.title}</Heading>
								<p>{guide.description}</p>
							</div>
							<span aria-hidden="true">→</span>
						</Link>
					))}
				</div>
			</div>
		</section>
	);
}

export default function Home(): ReactNode {
	const { siteConfig } = useDocusaurusContext();
	return (
		<Layout
			title={siteConfig.title}
			description="Hephaestus is an open-source AI mentor for software teams. It reads the work developers already do against the practices their project cares about, and writes back feedback they can act on."
		>
			<HomepageHeader />
			<main>
				<QuickstartGuides />
				<HomepageFeatures />
			</main>
		</Layout>
	);
}
