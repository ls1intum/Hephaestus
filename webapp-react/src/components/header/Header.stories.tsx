import type { Meta, StoryObj } from "@storybook/react";
import Header from "./Header";
import { BrowserRouter } from "react-router-dom";

// Mock the auth context for storybook
const mockAuthContext = {
  isAuthenticated: true,
  isLoading: false,
  username: "johnDoe",
  userRoles: ["admin", "mentor_access"],
  login: () => console.log("Login clicked"),
  logout: () => console.log("Logout clicked"),
};

// Mock theme context
const mockThemeContext = {
  theme: "light",
  setTheme: (theme: string) => console.log("Theme changed to:", theme),
};

jest.mock("@/lib/auth/AuthContext", () => ({
  useAuth: () => mockAuthContext,
}));

jest.mock("@/lib/theme/ThemeContext", () => ({
  useTheme: () => mockThemeContext,
}));

// Mock the environment for storybook
jest.mock("@/environment", () => ({
  default: {
    version: "1.0.0-storybook",
  }
}));

// For Tanstack Router, we need this mock to avoid router errors in Storybook
jest.mock("@tanstack/react-router", () => ({
  Link: ({ to, children, className, params }: any) => (
    <a href="#" className={className} data-to={to} data-params={params ? JSON.stringify(params) : undefined}>
      {children}
    </a>
  ),
}));

const meta = {
  title: "Components/Header",
  component: Header,
  parameters: {
    layout: "fullscreen",
  },
  decorators: [
    (Story) => (
      <BrowserRouter>
        <Story />
      </BrowserRouter>
    ),
  ],
  tags: ["autodocs"],
} satisfies Meta<typeof Header>;

export default meta;
type Story = StoryObj<typeof meta>;

export const LoggedIn: Story = {
  parameters: {
    mockData: [
      {
        match: {
          func: "useAuth",
        },
        data: mockAuthContext,
      },
      {
        match: {
          func: "useTheme",
        },
        data: mockThemeContext,
      },
    ],
  },
};

export const LoggedOut: Story = {
  parameters: {
    mockData: [
      {
        match: {
          func: "useAuth",
        },
        data: {
          ...mockAuthContext,
          isAuthenticated: false,
          username: null,
          userRoles: [],
        },
      },
      {
        match: {
          func: "useTheme",
        },
        data: mockThemeContext,
      },
    ],
  },
};

export const Loading: Story = {
  parameters: {
    mockData: [
      {
        match: {
          func: "useAuth",
        },
        data: {
          ...mockAuthContext,
          isAuthenticated: false,
          isLoading: true,
          username: null,
          userRoles: [],
        },
      },
      {
        match: {
          func: "useTheme",
        },
        data: mockThemeContext,
      },
    ],
  },
};

export const DarkMode: Story = {
  parameters: {
    mockData: [
      {
        match: {
          func: "useAuth",
        },
        data: mockAuthContext,
      },
      {
        match: {
          func: "useTheme",
        },
        data: {
          ...mockThemeContext,
          theme: "dark",
        },
      },
    ],
  },
};

export const NonAdmin: Story = {
  parameters: {
    mockData: [
      {
        match: {
          func: "useAuth",
        },
        data: {
          ...mockAuthContext,
          userRoles: ["mentor_access"],
        },
      },
      {
        match: {
          func: "useTheme",
        },
        data: mockThemeContext,
      },
    ],
  },
};