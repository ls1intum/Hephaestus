import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import {
	Code2,
	Copy,
	Download,
	Edit,
	Eye,
	FileText,
	Play,
	RefreshCw,
	Save,
	Share,
} from "lucide-react";
import { type ArtifactAction, ArtifactActions } from "./ArtifactActions";

/**
 * ArtifactActions component displays a row of action buttons for artifacts.
 * Designed to be purely presentational with actions configured by the parent component.
 */
const meta = {
	component: ArtifactActions,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		actions: {
			description: "Array of available actions for the artifact",
			control: "object",
		},
		isLoading: {
			description: "Whether any action is currently loading",
			control: "boolean",
		},
		isStreaming: {
			description: "Whether the artifact is currently streaming",
			control: "boolean",
		},
		className: {
			description: "Additional CSS classes for styling",
		},
	},
	args: {
		isLoading: false,
		isStreaming: false,
	},
} satisfies Meta<typeof ArtifactActions>;

export default meta;
type Story = StoryObj<typeof meta>;

// Sample actions for different artifact types
const basicActions: ArtifactAction[] = [
	{
		id: "copy",
		icon: <Copy size={14} />,
		description: "Copy to clipboard",
		onClick: fn(),
	},
	{
		id: "download",
		icon: <Download size={14} />,
		description: "Download file",
		onClick: fn(),
	},
	{
		id: "share",
		icon: <Share size={14} />,
		description: "Share artifact",
		onClick: fn(),
	},
];

const codeActions: ArtifactAction[] = [
	{
		id: "copy",
		icon: <Copy size={14} />,
		description: "Copy code",
		onClick: fn(),
	},
	{
		id: "run",
		icon: <Play size={14} />,
		description: "Run code",
		onClick: fn(),
	},
	{
		id: "edit",
		icon: <Edit size={14} />,
		description: "Edit code",
		onClick: fn(),
	},
	{
		id: "download",
		icon: <Download size={14} />,
		description: "Download as file",
		onClick: fn(),
	},
];

const documentActions: ArtifactAction[] = [
	{
		id: "copy",
		icon: <Copy size={14} />,
		description: "Copy content",
		onClick: fn(),
	},
	{
		id: "edit",
		icon: <Edit size={14} />,
		label: "Edit",
		description: "Edit document",
		onClick: fn(),
	},
	{
		id: "save",
		icon: <Save size={14} />,
		label: "Save",
		description: "Save changes",
		disabled: true,
		onClick: fn(),
	},
	{
		id: "preview",
		icon: <Eye size={14} />,
		description: "Preview document",
		onClick: fn(),
	},
];

/**
 * Basic actions without labels for compact display.
 */
export const Basic: Story = {
	args: {
		actions: basicActions,
	},
};

/**
 * Code-specific actions for programming artifacts.
 */
export const CodeActions: Story = {
	args: {
		actions: codeActions,
	},
};

/**
 * Document actions with mixed icon-only and labeled buttons.
 */
export const DocumentActions: Story = {
	args: {
		actions: documentActions,
	},
};

/**
 * Actions in loading state - all buttons disabled.
 */
export const Loading: Story = {
	args: {
		actions: codeActions,
		isLoading: true,
	},
};

/**
 * Actions during streaming - all buttons disabled.
 */
export const Streaming: Story = {
	args: {
		actions: codeActions,
		isStreaming: true,
	},
};

/**
 * Some actions disabled individually.
 */
export const WithDisabledActions: Story = {
	args: {
		actions: [
			{
				id: "copy",
				icon: <Copy size={14} />,
				description: "Copy to clipboard",
				onClick: fn(),
			},
			{
				id: "edit",
				icon: <Edit size={14} />,
				description: "Edit (unavailable)",
				disabled: true,
				onClick: fn(),
			},
			{
				id: "save",
				icon: <Save size={14} />,
				label: "Save",
				description: "Save changes (unavailable)",
				disabled: true,
				onClick: fn(),
			},
			{
				id: "download",
				icon: <Download size={14} />,
				description: "Download file",
				onClick: fn(),
			},
		],
	},
};

/**
 * Empty actions array - component renders nothing.
 */
export const EmptyActions: Story = {
	args: {
		actions: [],
	},
};

/**
 * Comprehensive action set showing various artifact capabilities.
 */
export const ComprehensiveActions: Story = {
	args: {
		actions: [
			{
				id: "copy",
				icon: <Copy size={14} />,
				description: "Copy to clipboard",
				onClick: fn(),
			},
			{
				id: "edit",
				icon: <Edit size={14} />,
				label: "Edit",
				description: "Edit content",
				onClick: fn(),
			},
			{
				id: "run",
				icon: <Play size={14} />,
				description: "Execute code",
				onClick: fn(),
			},
			{
				id: "refresh",
				icon: <RefreshCw size={14} />,
				description: "Refresh content",
				onClick: fn(),
			},
			{
				id: "view-code",
				icon: <Code2 size={14} />,
				description: "View source",
				onClick: fn(),
			},
			{
				id: "export",
				icon: <FileText size={14} />,
				label: "Export",
				description: "Export as document",
				onClick: fn(),
			},
			{
				id: "share",
				icon: <Share size={14} />,
				description: "Share artifact",
				onClick: fn(),
			},
		],
	},
};
