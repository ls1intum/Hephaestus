import type { PageProps } from "keycloakify/login/pages/PageProps";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Button } from "@/components/ui/button";

export default function Info(props: PageProps<Extract<KcContext, { pageId: "info.ftl" }>, I18n>) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;

    const { advancedMsgStr, msg } = i18n;

    const { messageHeader, message, requiredActions, skipLink, pageRedirectUri, actionUri, client } = kcContext;

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            displayMessage={false}
            headerNode={
                <span
                    dangerouslySetInnerHTML={{
                        __html: kcSanitize(messageHeader ?? message.summary)
                    }}
                />
            }
        >
            <div id="kc-info-message">
                <p
                    className="instruction"
                    dangerouslySetInnerHTML={{
                        __html: kcSanitize(
                            (() => {
                                let html = message.summary;

                                if (requiredActions) {
                                    html += "<b>";

                                    html += requiredActions.map(requiredAction => advancedMsgStr(`requiredAction.${requiredAction}`)).join(", ");

                                    html += "</b>";
                                }

                                return html;
                            })()
                        )
                    }}
                />
                {(() => {
                    if (skipLink) {
                        return null;
                    }

                    if (pageRedirectUri) {
                        return (
                            <p className="mt-2">
                                <Button asChild variant="ghost">
                                    <a href={pageRedirectUri}>{msg("backToApplication")}</a>
                                </Button>
                            </p>
                        );
                    }
                    if (actionUri) {
                        return (
                            <p className="mt-2">
                                <Button asChild variant="ghost">
                                    <a href={actionUri}>{msg("proceedWithAction")}</a>
                                </Button>
                            </p>
                        );
                    }

                    if (client.baseUrl) {
                        return (
                            <p className="mt-2">
                                <Button asChild variant="ghost">
                                    <a href={client.baseUrl}>{msg("backToApplication")}</a>
                                </Button>
                            </p>
                        );
                    }
                })()}
            </div>
        </Template>
    );
}
