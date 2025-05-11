import type { Meta, StoryObj } from "@storybook/react";
import Footer from "./Footer";
import { BrowserRouter } from "react-router-dom";

// Mock the environment for storybook
jest.mock("@/environment", () => ({
  default: {
    version: "1.0.0-storybook",
  }
}));

// For Tanstack Router, we need this mock to avoid router errors in Storybook
jest.mock("@tanstack/react-router", () => ({
  Link: ({ to, children, className }: any) => (
    <a href="#" className={className}>{children}</a>
  ),
}));

const meta = {
  title: "Components/Footer",
  component: Footer,
  parameters: {
    layout: "fullscreen",
  },
  decorators: [
    (Story) => (
      <BrowserRouter>
        <div className="min-h-screen flex flex-col">
          <div className="flex-1"></div>
          <Story />
        </div>
      </BrowserRouter>
    ),
  ],
  tags: ["autodocs"],
} satisfies Meta<typeof Footer>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};