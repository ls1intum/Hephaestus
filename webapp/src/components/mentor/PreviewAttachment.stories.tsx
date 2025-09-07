import type { Meta, StoryObj } from "@storybook/react";
import { PreviewAttachment } from "./PreviewAttachment";

/**
 * PreviewAttachment component displays file attachment previews with upload states.
 * Shows thumbnail images for image files and handles loading states during upload.
 * Perfect for file upload interfaces and attachment management.
 */
const meta = {
	component: PreviewAttachment,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		attachment: {
			description: "Attachment object containing name, url, and contentType",
			control: "object",
		},
		isUploading: {
			description: "Whether the attachment is currently being uploaded",
			control: "boolean",
		},
	},
	args: {
		isUploading: false,
	},
} satisfies Meta<typeof PreviewAttachment>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default image attachment preview.
 */
export const Default: Story = {
	args: {
		attachment: {
			name: "landscape.jpg",
			url: "https://picsum.photos/200/150",
			contentType: "image/jpeg",
		},
	},
};

/**
 * Image attachment currently being uploaded with loading indicator.
 */
export const Uploading: Story = {
	args: {
		attachment: {
			name: "photo.png",
			url: "https://picsum.photos/seed/upload/200/150",
			contentType: "image/png",
		},
		isUploading: true,
	},
};

/**
 * Image attachment with a very long filename that gets truncated.
 */
export const LongFilename: Story = {
	args: {
		attachment: {
			name: "very-long-filename-that-should-be-truncated-in-the-preview.jpg",
			url: "https://picsum.photos/seed/longname/200/150",
			contentType: "image/jpeg",
		},
	},
};

/**
 * PNG image attachment with different dimensions.
 */
export const PngImage: Story = {
	args: {
		attachment: {
			name: "screenshot.png",
			url: "https://picsum.photos/seed/png/300/200",
			contentType: "image/png",
		},
	},
};

/**
 * Non-image attachment (should show placeholder).
 */
export const PdfDocument: Story = {
	args: {
		attachment: {
			name: "document.pdf",
			url: "https://example.com/document.pdf",
			contentType: "application/pdf",
		},
	},
};
