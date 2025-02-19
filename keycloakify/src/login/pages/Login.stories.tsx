import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "login.ftl" });

const meta = {
    title: "login/login.ftl",
    component: KcPageStory
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: args => (
        <KcPageStory
            {...args}
            kcContext={{
                social: {
                    displayInfo: true,
                    providers: [
                        {
                            loginUrl: "github",
                            alias: "github",
                            providerId: "github",
                            displayName: "Github",
                            iconClasses: "fa fa-github"
                        }
                    ]
                }
            }}
        />
    )
};

export const WithAllSocialProviders: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                social: {
                    displayInfo: true,
                    providers: [
                        {
                            loginUrl: "github",
                            alias: "github",
                            providerId: "github",
                            displayName: "Github",
                            iconClasses: "fa fa-github"
                        },
                        {
                            loginUrl: "gitlab",
                            alias: "gitlab",
                            providerId: "gitlab",
                            displayName: "Gitlab",
                            iconClasses: "fa fa-gitlab"
                        }
                    ]
                }
            }}
        />
    )
};

export const WithErrorMessage: Story = {
    render: args => (
        <KcPageStory
            {...args}
            kcContext={{
                message: {
                    summary: "The time allotted for the connection has elapsed.<br/>The login process will restart from the beginning.",
                    type: "error"
                },
                social: {
                    displayInfo: true,
                    providers: [
                        {
                            loginUrl: "github",
                            alias: "github",
                            providerId: "github",
                            displayName: "Github",
                            iconClasses: "fa fa-github"
                        },
                        {
                            loginUrl: "gitlab",
                            alias: "gitlab",
                            providerId: "gitlab",
                            displayName: "Gitlab",
                            iconClasses: "fa fa-gitlab"
                        }
                    ]
                }
            }}
        />
    )
};
