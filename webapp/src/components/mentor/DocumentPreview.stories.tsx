import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import type { Document } from "@/lib/types";
import { DocumentPreview } from "./DocumentPreview";

/**
 * DocumentPreview displays a document with its content in a preview format.
 * A purely presentational component that handles document display, streaming states,
 * and user interactions without any data fetching logic.
 */
const meta = {
	component: DocumentPreview,
	tags: ["autodocs"],
	argTypes: {
		isLoading: {
			description: "Whether the document is currently being loaded",
			control: "boolean",
		},
		isStreaming: {
			description: "Whether the document content is currently streaming",
			control: "boolean",
		},
	},
	args: {
		onDocumentClick: fn(),
		isLoading: false,
		isStreaming: false,
	},
} satisfies Meta<typeof DocumentPreview>;

export default meta;
type Story = StoryObj<typeof meta>;

// Sample documents for stories
const sampleDocument: Document = {
	id: "doc-123",
	title: "Project Requirements",
	content: `# Project Requirements

This document outlines the key requirements for our upcoming project.

## Overview
The goal is to create a user-friendly application that meets all stakeholder needs.

## Key Features
- User authentication
- Dashboard interface
- Data visualization
- Export functionality

## Timeline
Expected completion: Q2 2024`,
	kind: "text",
	createdAt: new Date("2024-01-15"),
	userId: 456,
	versionNumber: 1,
};

const longDocument: Document = {
	id: "doc-789",
	title: "Technical Specification",
	content: `# Technical Specification

## Introduction
This document provides a comprehensive technical specification for the project.

## Architecture Overview
The system follows a microservices architecture with the following components:

### Frontend
- React 18 with TypeScript
- Tailwind CSS for styling
- Tanstack Query for data management

### Backend
- Spring Boot application
- PostgreSQL database
- Redis for caching

## API Endpoints

### Authentication
- POST /api/auth/login
- POST /api/auth/logout
- GET /api/auth/me

### Documents
- GET /api/documents
- POST /api/documents
- PUT /api/documents/{id}
- DELETE /api/documents/{id}

## Database Schema

### Users Table
- id (UUID, Primary Key)
- email (VARCHAR, Unique)
- name (VARCHAR)
- created_at (TIMESTAMP)

### Documents Table
- id (UUID, Primary Key)
- title (VARCHAR)
- content (TEXT)
- user_id (UUID, Foreign Key)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

## Security Considerations
- JWT token authentication
- HTTPS encryption
- Input validation
- SQL injection prevention

## Performance Requirements
- Response time < 200ms for API calls
- Support for 1000+ concurrent users
- 99.9% uptime requirement

## Deployment
The application will be deployed using Docker containers on AWS ECS.`,
	kind: "text",
	createdAt: new Date("2024-02-01"),
	userId: 789,
	versionNumber: 1,
};

/**
 * Loading state while document is being fetched.
 */
export const Loading: Story = {
	args: {
		document: null,
		isLoading: true,
	},
};

/**
 * Basic document preview with short content.
 */
export const BasicDocument: Story = {
	args: {
		document: sampleDocument,
	},
};

/**
 * Document with longer content to test scrolling behavior.
 */
export const LongDocument: Story = {
	args: {
		document: longDocument,
	},
};

/**
 * Document in streaming mode showing live content updates.
 */
export const Streaming: Story = {
	args: {
		document: {
			...sampleDocument,
			content: "# Live Document\n\nContent is being generated...",
		},
		isStreaming: true,
	},
};

/**
 * Empty document with minimal content.
 */
export const EmptyDocument: Story = {
	args: {
		document: {
			id: "doc-empty",
			title: "Untitled Document",
			content: "",
			kind: "text",
			createdAt: new Date(),
			userId: 123,
			versionNumber: 1,
		},
	},
};

/**
 * Document with markdown formatting.
 */
export const MarkdownDocument: Story = {
	args: {
		document: {
			id: "doc-markdown",
			title: "Meeting Notes",
			content: `# Team Meeting Notes
			
## Date: March 15, 2024

### Attendees
- **John Doe** (Project Manager)
- *Jane Smith* (Developer)
- Alice Johnson (Designer)

### Action Items
1. Complete wireframes by Friday
2. Set up development environment
3. Schedule user testing session

### Next Meeting
**Date:** March 22, 2024  
**Time:** 2:00 PM EST

> Remember to bring laptops for the demo!`,
			kind: "text",
			createdAt: new Date("2024-03-15"),
			userId: 456,
			versionNumber: 1,
		},
	},
};

/**
 * Document with code examples and technical content.
 */
export const TechnicalDocument: Story = {
	args: {
		document: {
			id: "doc-technical",
			title: "API Integration Guide",
			content: `# API Integration Guide

## Authentication
All API requests require authentication using Bearer tokens.

### Example Request
\`\`\`bash
curl -H "Authorization: Bearer YOUR_TOKEN" \\
     https://api.example.com/documents
\`\`\`

### Response Format
\`\`\`json
{
  "data": [
    {
      "id": "doc-123",
      "title": "Sample Document",
      "content": "Document content here..."
    }
  ]
}
\`\`\`

## Error Handling
The API returns standard HTTP status codes:

- **200** - Success
- **400** - Bad Request
- **401** - Unauthorized
- **404** - Not Found
- **500** - Internal Server Error`,
			kind: "text",
			createdAt: new Date("2024-02-20"),
			userId: 789,
			versionNumber: 1,
		},
	},
};
