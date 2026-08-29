import type * as Preset from "@docusaurus/preset-classic";
import type { Config } from "@docusaurus/types";
import { themes as prismThemes } from "prism-react-renderer";

const envBaseUrl = process.env.DOCUSAURUS_BASE_URL;

/*
 * PR previews on Surge.sh need '/', production GitHub Pages needs '/Hephaestus/'. Unset and empty
 * both mean production: an empty base URL would resolve every asset against the site root.
 */
const baseUrl = envBaseUrl === undefined || envBaseUrl === "" ? "/Hephaestus/" : envBaseUrl;

/** The site's one-sentence definition of the product, kept in step with the README's opening. */
const DESCRIPTION =
	"Hephaestus is an open-source AI mentor for software teams. It reads the work developers already do against the practices their project cares about, and writes back feedback they can act on.";

const config: Config = {
	title: "Hephaestus Documentation",
	tagline: "Learn from the work you're already doing",
	favicon: "img/favicon.ico",

	future: {
		v4: true,
		faster: {
			swcJsLoader: true,
			swcJsMinimizer: true,
			swcHtmlMinimizer: true,
			lightningCssMinimizer: true,
			rspackBundler: true,
			mdxCrossCompilerCache: true,
		},
	},

	url: "https://ls1intum.github.io",
	baseUrl,
	organizationName: "ls1intum",
	projectName: "Hephaestus",

	onBrokenLinks: "throw",
	onBrokenAnchors: "throw",
	onDuplicateRoutes: "throw",
	trailingSlash: false,

	i18n: {
		defaultLocale: "en",
		locales: ["en"],
	},

	customFields: {
		productUrl: "https://hephaestus.aet.cit.tum.de",
		repoUrl: "https://github.com/ls1intum/Hephaestus",
	},

	markdown: {
		mermaid: true,
		hooks: {
			onBrokenMarkdownLinks: "throw",
			onBrokenMarkdownImages: "throw",
		},
	},

	themes: ["@docusaurus/theme-mermaid"],

	presets: [
		[
			"classic",
			{
				docs: false,
				blog: false,
				theme: {
					customCss: "./src/css/custom.css",
				},
				sitemap: {
					lastmod: "datetime",
					changefreq: "weekly",
					priority: 0.5,
					filename: "sitemap.xml",
				},
			} satisfies Preset.Options,
		],
	],

	plugins: [
		[
			"@docusaurus/plugin-content-docs",
			{
				id: "default",
				path: "./user",
				routeBasePath: "user",
				sidebarPath: "./sidebars.user.ts",
				editUrl: "https://github.com/ls1intum/Hephaestus/tree/main/docs/",
				showLastUpdateAuthor: true,
				showLastUpdateTime: true,
			},
		],
		[
			"@docusaurus/plugin-content-docs",
			{
				id: "contributor-docs",
				path: "./contributor",
				routeBasePath: "contributor",
				sidebarPath: "./sidebars.contributor.ts",
				editUrl: "https://github.com/ls1intum/Hephaestus/tree/main/docs/",
				showLastUpdateAuthor: true,
				showLastUpdateTime: true,
			},
		],
		[
			"@docusaurus/plugin-content-docs",
			{
				id: "admin-docs",
				path: "./admin",
				routeBasePath: "admin",
				sidebarPath: "./sidebars.admin.ts",
				editUrl: "https://github.com/ls1intum/Hephaestus/tree/main/docs/",
				showLastUpdateAuthor: true,
				showLastUpdateTime: true,
			},
		],
		[
			"@easyops-cn/docusaurus-search-local",
			{
				hashed: true,
				language: ["en"],
				indexBlog: false,
				docsRouteBasePath: ["user", "contributor", "admin"],
				docsDir: ["user", "contributor", "admin"],
				searchContextByPaths: [
					{ label: { en: "User Guide" }, path: "user" },
					{ label: { en: "Contributor Guide" }, path: "contributor" },
					{ label: { en: "Admin Guide" }, path: "admin" },
				],
				hideSearchBarWithNoSearchContext: false,
				useAllContextsWithNoSearchContext: true,
				highlightSearchTermsOnTargetPage: true,
				searchResultContextMaxLength: 60,
			},
		],
	],

	themeConfig: {
		image: "img/hephaestus-social-card.jpg",
		colorMode: {
			respectPrefersColorScheme: true,
			disableSwitch: false,
		},
		metadata: [
			{ name: "description", content: DESCRIPTION },
			{
				name: "keywords",
				content: "Hephaestus, AI mentor, code review feedback, software engineering practices, TUM",
			},
			{ name: "twitter:card", content: "summary_large_image" },
			{ name: "twitter:site", content: "@ls1intum" },
			{ name: "twitter:title", content: "Hephaestus Documentation" },
			{ name: "twitter:description", content: DESCRIPTION },
		],
		navbar: {
			title: "Hephaestus",
			logo: {
				alt: "Hephaestus logo",
				src: "img/hammer.svg",
			},
			items: [
				{
					type: "docSidebar",
					sidebarId: "userSidebar",
					docsPluginId: "default",
					position: "left",
					label: "User Guide",
				},
				{
					type: "docSidebar",
					sidebarId: "contributorSidebar",
					docsPluginId: "contributor-docs",
					position: "left",
					label: "Contributor Guide",
				},
				{
					type: "docSidebar",
					sidebarId: "adminSidebar",
					docsPluginId: "admin-docs",
					position: "left",
					label: "Admin Guide",
				},

				{
					href: "https://hephaestus.aet.cit.tum.de",
					label: "Open Hephaestus",
					position: "right",
				},
				{
					href: "https://github.com/ls1intum/Hephaestus",
					position: "right",
					className: "navbarGithubLink",
					"aria-label": "Hephaestus on GitHub",
				},
			],
		},
		footer: {
			style: "light",
			links: [
				{
					title: "Product",
					items: [
						{
							label: "User Guide",
							to: "/user/overview",
						},
						{
							label: "Admin Guide",
							to: "/admin/overview",
						},
						{
							label: "Release Notes",
							href: "https://github.com/ls1intum/Hephaestus/releases",
						},
						{
							label: "Accessibility",
							to: "/user/accessibility",
						},
						{
							label: "Open Hephaestus",
							href: "https://hephaestus.aet.cit.tum.de",
						},
					],
				},
				{
					title: "Contribute",
					items: [
						{
							label: "Contributor Guide",
							to: "/contributor/overview",
						},
						{
							label: "Feature Requests",
							href: "https://github.com/ls1intum/Hephaestus/discussions/categories/feature-requests",
						},
						{
							label: "Bug Tracker",
							href: "https://github.com/ls1intum/Hephaestus/issues",
						},
					],
				},
				{
					title: "Project",
					items: [
						{
							label: "Applied Education Technologies",
							href: "https://aet.cit.tum.de/",
						},
						{
							label: "GitHub Repository",
							href: "https://github.com/ls1intum/Hephaestus",
						},
						{
							label: "Imprint",
							href: "https://hephaestus.aet.cit.tum.de/imprint",
						},
						{
							label: "Privacy",
							href: "https://hephaestus.aet.cit.tum.de/privacy",
						},
					],
				},
			],
			copyright: `Built by <a href="https://github.com/ls1intum">AET Team</a> at <a href="https://www.tum.de/en/">TUM</a>. Source on <a href="https://github.com/ls1intum/Hephaestus">GitHub</a>.`,
		},
		announcementBar: {
			id: "pre-1-0",
			// Raw HTML: Docusaurus neither prefixes this href with the base URL nor reports it to
			// `onBrokenLinks`, so it has to carry the base URL itself or 404 on every page.
			content: `Hephaestus is <strong>pre-1.0</strong>: only the latest release is supported, and a minor release can require action. <a href="${baseUrl}admin/compatibility-policy">Read the compatibility policy</a>.`,
			isCloseable: true,
		},
		docs: {
			sidebar: {
				hideable: true,
				autoCollapseCategories: true,
			},
		},
		tableOfContents: {
			minHeadingLevel: 2,
			maxHeadingLevel: 4,
		},
		prism: {
			theme: prismThemes.vsLight,
			darkTheme: prismThemes.vsDark,
			additionalLanguages: ["bash", "json", "yaml", "java"],
		},
	} satisfies Preset.ThemeConfig,
};

export default config;
