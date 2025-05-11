import type { Meta, StoryObj } from "@storybook/react";
import { action } from "@storybook/addon-actions";
import Header from "./Header";

const meta = {
  title: "Components/Header",
  component: Header,
  parameters: {
    layout: "fullscreen",
  },
  tags: ["autodocs"],
  args: {
    version: "1.0.0",
    name: "John Doe",
    username: "johnDoe",
    onLogin: action("login clicked"),
    onLogout: action("logout clicked"),
  },
  argTypes: {
    showAdmin: { control: 'boolean' },
    showMentor: { control: 'boolean' },
    isAuthenticated: { control: 'boolean' },
    isLoading: { control: 'boolean' },
    name: { control: 'text' },
    username: { control: 'text' },
    version: { control: 'text' },
  },
} satisfies Meta<typeof Header>;

export default meta;
type Story = StoryObj<typeof meta>;

// Different story variants with direct props instead of context mocking
export const LoggedInAdmin: Story = {
  args: {
    isAuthenticated: true,
    isLoading: false,
    showAdmin: true,
    showMentor: true,
  },
};

export const LoggedInNonAdmin: Story = {
  args: {
    isAuthenticated: true,
    isLoading: false,
    showAdmin: false,
    showMentor: true,
  },
};

export const LoggedOut: Story = {
  args: {
    isAuthenticated: false,
    isLoading: false,
    showAdmin: false,
    showMentor: false,
  },
};

export const Loading: Story = {
  args: {
    isAuthenticated: false,
    isLoading: true,
    showAdmin: false,
    showMentor: false,
  },
};