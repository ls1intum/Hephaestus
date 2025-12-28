import type { Meta, StoryObj } from "@storybook/react-vite";
import { DocumentSkeleton, InlineDocumentSkeleton } from "./DocumentSkeleton";

/**
 * DocumentSkeleton components provide loading placeholders for document content.
 * The main DocumentSkeleton adapts its layout based on artifact type, while InlineDocumentSkeleton
 * provides a compact variant for smaller content areas like sidebars and lists.
 */
const meta = {
	component: DocumentSkeleton,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		artifactKind: {
			description: "Type of artifact being loaded - determines skeleton layout and structure",
			control: "select",
			options: ["text"],
		},
	},
	args: {
		artifactKind: "text",
	},
	decorators: [
		(Story) => (
			<div className="max-w-2xl w-full p-6 bg-background">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof DocumentSkeleton>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default document skeleton showing structured text content loading placeholder.
 * Displays header, multiple content lines, and action areas typical of document artifacts.
 */
export const Default: Story = {};

/**
 * Text artifact skeleton optimized for document content with headers, paragraphs, and actions.
 * This is the current implementation - future artifact types will have specialized layouts.
 */
export const TextArtifact: Story = {
	args: {
		artifactKind: "text",
	},
};
/**
 * Compact inline document skeleton for smaller content areas like sidebars and lists.
 * Used for document previews, search results, and related content sections.
 */
export const InlineDefault: Story = {
	render: () => <InlineDocumentSkeleton />,
};
