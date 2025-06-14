import type { Meta, StoryObj } from "@storybook/react";
import { LegalPage } from "./LegalPage";

/**
 * Legal page component for displaying Imprint and Privacy Policy information.
 * Renders HTML content with proper styling.
 */
const meta = {
	component: LegalPage,
	tags: ["autodocs"],
	argTypes: {
		title: {
			description: "Title displayed at the top of the legal page",
			control: "text",
		},
		content: {
			description: "HTML content to be rendered on the page",
			control: "text",
		},
	},
} satisfies Meta<typeof LegalPage>;

export default meta;
type Story = StoryObj<typeof meta>;

// Example imprint content for the story
const imprintContent = `
  <h2>Project Information</h2>
  <p>Hephaestus Project<br/>
  Technical University of Munich<br/>
  Department of Informatics<br/>
  Chair for Applied Software Engineering<br/>
  Germany</p>

  <h2>Contact</h2>
  <p>Email: info@example.com<br/>
  Phone: +49 123 456789</p>

  <h2>Project Lead</h2>
  <p>Felix T.J. Dietrich</p>

  <h2>Data Protection</h2>
  <p>We take data protection seriously. See our Privacy Policy for details.</p>
`;

// Example privacy policy content for the story
const privacyContent = `
  <h2>Privacy Policy</h2>
  <p>Last updated: May 10, 2025</p>
  
  <h3>1. Introduction</h3>
  <p>This Privacy Policy explains how the Hephaestus project ("we", "us", or "our") collects, uses, and shares your personal information when you use our services.</p>
  
  <h3>2. Information We Collect</h3>
  <p>We may collect the following types of information:</p>
  <ul>
    <li>Personal information provided during registration (name, email address)</li>
    <li>GitHub profile information when you connect your GitHub account</li>
    <li>Usage data regarding your interactions with our platform</li>
  </ul>
  
  <h3>3. How We Use Your Information</h3>
  <p>We use the information we collect to:</p>
  <ul>
    <li>Provide, maintain, and improve our services</li>
    <li>Process and analyze GitHub repository data to provide insights</li>
    <li>Communicate with you about your account or our services</li>
  </ul>
  
  <h3>4. Data Storage and Security</h3>
  <p>We implement appropriate security measures to protect your personal information.</p>
  
  <h3>5. Contact Us</h3>
  <p>If you have any questions about this Privacy Policy, please contact us at privacy@example.com.</p>
`;

/**
 * Imprint legal information showing project details, contacts, and data protection.
 */
/**
 * Imprint page displaying legal contact and responsible entity information.
 */
export const Imprint: Story = {
	args: {
		title: "Imprint",
		content: imprintContent,
	},
};

/**
 * Privacy Policy detailing how user data is collected, used, and protected.
 */
export const Privacy: Story = {
	args: {
		title: "Privacy Policy",
		content: privacyContent,
	},
};
