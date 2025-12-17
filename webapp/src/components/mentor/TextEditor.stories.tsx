import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { useEffect, useState } from "react";
import { TextEditor } from "./TextEditor";

/**
 * TextEditor is a rich text editor powered by ProseMirror.
 * Supports markdown-style input, live content streaming, and version control.
 */
const meta = {
	component: TextEditor,
	tags: ["autodocs"],
	argTypes: {
		status: {
			description: "Editor status - streaming shows live content updates",
			control: "select",
			options: ["idle", "streaming"],
		},
		isCurrentVersion: {
			description: "Whether this is the current/latest version",
			control: "boolean",
		},
	},
	args: {
		onSaveContent: fn(),
		status: "idle",
		isCurrentVersion: true,
	},
} satisfies Meta<typeof TextEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Empty editor ready for new content creation.
 */
export const Empty: Story = {
	args: {
		content: "",
	},
};

/**
 * Editor with basic text content.
 */
export const BasicText: Story = {
	args: {
		content:
			"Welcome to the text editor! Start typing to see the rich text features in action.",
	},
};

/**
 * Editor with markdown-formatted content including headings and lists.
 */
export const MarkdownContent: Story = {
	args: {
		content: `# Project Overview

This document outlines the key objectives and requirements for our upcoming project.

## Goals
- Improve user experience
- Increase performance by 20%
- Implement new features

## Timeline
The project is expected to take **3 months** to complete.

### Phase 1: Research
Initial research and planning phase.

### Phase 2: Development
Core development and implementation.

### Phase 3: Testing
Quality assurance and testing phase.`,
	},
};

/**
 * Editor in streaming mode showing live content updates.
 */
export const Streaming: Story = {
	args: {
		content: "",
	},
	render: (args) => {
		const StreamingDemo = () => {
			const [content, setContent] = useState("");

			const fullContent = `# Live Document Stream

This content is being generated in real-time as the AI processes your request.

## Current Progress
- Research phase completed
- Analysis in progress
- Initial draft being written...

## Key Findings
The data shows interesting patterns that require further investigation and deeper analysis.

## Next Steps
We will continue to expand this document with more detailed information as it becomes available.`;

			useEffect(() => {
				let currentIndex = 0;
				const interval = setInterval(() => {
					if (currentIndex < fullContent.length) {
						setContent(fullContent.slice(0, currentIndex + 1));
						currentIndex++;
					} else {
						// Reset and start over
						currentIndex = 0;
						setContent("");
					}
				}, 5);

				return () => clearInterval(interval);
			}, []);

			return <TextEditor {...args} status="streaming" content={content} />;
		};

		return <StreamingDemo />;
	},
};

/**
 * Editor showing a previous version (not current).
 */
export const PreviousVersion: Story = {
	args: {
		isCurrentVersion: false,
		content: `# Document - Version 2

This is an older version of the document.

## Previous Content
This shows how the document looked in an earlier revision.`,
	},
};

/**
 * Editor with complex formatting including code blocks and emphasis.
 */
export const ComplexFormatting: Story = {
	args: {
		content: `# Technical Documentation

## Code Examples

Here's how to implement the function:

\`\`\`javascript
function calculateTotal(items) {
  return items.reduce((sum, item) => sum + item.price, 0);
}
\`\`\`

## Important Notes

- Always **validate** input data
- Use *proper* error handling
- Document your code thoroughly

### Best Practices
1. Write clean, readable code
2. Add comprehensive tests
3. Follow coding standards`,
	},
};

/**
 * Long document content to test scrolling and performance.
 */
export const LongContent: Story = {
	args: {
		content: `# Comprehensive Project Guide

## Introduction
This is a comprehensive guide that covers all aspects of the project development process.

## Chapter 1: Getting Started
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.

### Section 1.1: Prerequisites
- Node.js 18 or higher
- TypeScript knowledge
- React experience
- Git familiarity

### Section 1.2: Installation
Follow these steps to set up your development environment.

## Chapter 2: Development Process
Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Mauris viverra veniam sit amet lacus cursus, eu ornare libero.

### Section 2.1: Planning Phase
Detailed planning is crucial for project success.

### Section 2.2: Implementation
Start with the core functionality and iterate.

## Chapter 3: Testing and Deployment
Comprehensive testing ensures quality and reliability.

### Section 3.1: Unit Testing
Write tests for individual components and functions.

### Section 3.2: Integration Testing
Test how different parts work together.

### Section 3.3: Deployment
Deploy to staging first, then production.

## Conclusion
Following these guidelines will help ensure project success.`,
	},
};
