import { useEffect } from "react";
import { clsx } from "keycloakify/tools/clsx";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { TemplateProps } from "keycloakify/login/TemplateProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import { useSetClassName } from "keycloakify/tools/useSetClassName";
import { useInitialize } from "keycloakify/login/Template.useInitialize";
import type { I18n } from "./i18n";
import type { KcContext } from "./KcContext";
import ThemeSwitcher from "@/components/theme-switcher";
import { CircleAlert, CircleCheck, CircleX, Hammer, Info } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

export default function Template(props: TemplateProps<KcContext, I18n>) {
    const {
        displayInfo = false,
        displayMessage = true,
        displayRequiredFields = false,
        headerNode,
        socialProvidersNode = null,
        infoNode = null,
        documentTitle,
        bodyClassName,
        kcContext,
        i18n,
        doUseDefaultCss,
        classes,
        children
    } = props;

    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { msg, msgStr } = i18n;

    const { auth, url, message, isAppInitiatedAction } = kcContext;

    useEffect(() => {
        document.title = documentTitle ?? msgStr("loginTitle", kcContext.realm.displayName);
    }, []);

    useSetClassName({
        qualifiedName: "html",
        className: kcClsx("kcHtmlClass")
    });

    useSetClassName({
        qualifiedName: "body",
        className: bodyClassName ?? kcClsx("kcBodyClass")
    });

    const { isReadyToRender } = useInitialize({ kcContext, doUseDefaultCss });

    if (!isReadyToRender) {
        return null;
    }

    return (
        <div className="flex flex-col gap-4 min-h-dvh">
            <header className="container flex items-center justify-end pt-4 gap-2">
                <ThemeSwitcher />
            </header>
            <div className="container flex flex-col items-center justify-center gap-6 flex-1">
                <div id="kc-header">
                    <div id="kc-header-wrapper" className="flex flex-col items-center gap-4">
                        <Hammer className="size-12" />
                        <span className="text-3xl font-medium">Hephaestus</span>
                    </div>
                </div>
                <div className="flex flex-col gap-4 mb-16 md:mb-32 w-96">
                    <div>
                        {(() => {
                            const node = !(auth !== undefined && auth.showUsername && !auth.showResetCredentials) ? (
                                <h1 id="kc-page-title" className="text-xl text-center">
                                    {headerNode}
                                </h1>
                            ) : (
                                <div id="kc-username" className={kcClsx("kcFormGroupClass")}>
                                    <label id="kc-attempted-username">{auth.attemptedUsername}</label>
                                    <a id="reset-login" href={url.loginRestartFlowUrl} aria-label={msgStr("restartLoginTooltip")}>
                                        <div className="kc-login-tooltip">
                                            <i className={kcClsx("kcResetFlowIcon")}></i>
                                            <span className="kc-tooltip-text">{msg("restartLoginTooltip")}</span>
                                        </div>
                                    </a>
                                </div>
                            );

                            if (displayRequiredFields) {
                                return (
                                    <div className={kcClsx("kcContentWrapperClass")}>
                                        <div className={clsx(kcClsx("kcLabelWrapperClass"), "subtitle flex justify-end")}>
                                            <span className="subtitle [&>div]:text-muted-foreground [&>div]:text-sm">
                                                <span className="required text-destructive mr-0.5">*</span>
                                                {msg("requiredFields")}
                                            </span>
                                        </div>
                                        <div className="col-md-10">{node}</div>
                                    </div>
                                );
                            }

                            return node;
                        })()}
                    </div>
                    <div id="kc-content">
                        <div id="kc-content-wrapper">
                            {/* App-initiated actions should not see warning messages about the need to complete the action during login. */}
                            {displayMessage && message !== undefined && (message.type !== "warning" || !isAppInitiatedAction) && (
                                <Alert variant={message.type} className="mb-2">
                                    {message.type === "error" && <CircleX className="h-4 w-4 flex-shrink-0" />}
                                    {message.type === "warning" && <CircleAlert className="h-4 w-4 flex-shrink-0" />}
                                    {message.type === "info" && <Info className="h-4 w-4 flex-shrink-0" />}
                                    {message.type === "success" && <CircleCheck className="h-4 w-4 flex-shrink-0" />}
                                    <AlertDescription>
                                        <span
                                            className={kcClsx("kcAlertTitleClass")}
                                            dangerouslySetInnerHTML={{
                                                __html: kcSanitize(message.summary)
                                            }}
                                        />
                                    </AlertDescription>
                                </Alert>
                            )}
                            {children}
                            {auth !== undefined && auth.showTryAnotherWayLink && (
                                <form id="kc-select-try-another-way-form" action={url.loginAction} method="post">
                                    <div className={kcClsx("kcFormGroupClass")}>
                                        <input type="hidden" name="tryAnotherWay" value="on" />
                                        <a
                                            href="#"
                                            id="try-another-way"
                                            onClick={() => {
                                                document.forms["kc-select-try-another-way-form" as never].submit();
                                                return false;
                                            }}
                                        >
                                            {msg("doTryAnotherWay")}
                                        </a>
                                    </div>
                                </form>
                            )}
                            {socialProvidersNode}
                            {displayInfo && (
                                <div id="kc-info" className={kcClsx("kcSignUpClass")}>
                                    <div id="kc-info-wrapper" className={kcClsx("kcInfoAreaWrapperClass")}>
                                        {infoNode}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
            <footer className="py-6 md:px-8 md:py-0 border-t">
                <div className="container flex flex-col items-center justify-between gap-2 md:gap-4 md:h-24 md:flex-row">
                    <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
                        <a href="/about" className="font-medium underline underline-offset-4">
                            About
                        </a>
                    </p>
                    <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
                        <a href="/privacy" className="font-medium underline underline-offset-4">
                            Privacy
                        </a>
                    </p>
                    <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
                        <a href="/imprint" className="font-medium underline underline-offset-4">
                            Imprint
                        </a>
                    </p>
                    <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-left">
                        <a data-canny-link href="https://hephaestus.canny.io/feature-requests" className="font-medium underline underline-offset-4">
                            Feature requests
                        </a>
                    </p>
                    <p className="text-balance text-center text-sm leading-loose text-muted-foreground md:text-right flex-1">
                        Built by{" "}
                        <a href="https://github.com/ls1intum" target="_blank" rel="noreferrer" className="font-medium underline underline-offset-4">
                            LS1 Team
                        </a>{" "}
                        at{" "}
                        <a href="https://www.tum.de/en/" target="_blank" rel="noreferrer" className="font-medium underline underline-offset-4">
                            TUM
                        </a>
                        . The source code is available on
                        <a
                            href="https://github.com/ls1intum/hephaestus"
                            target="_blank"
                            rel="noreferrer"
                            className="font-medium underline underline-offset-4"
                        >
                            {" "}
                            GitHub
                        </a>
                        .
                    </p>
                </div>
            </footer>
        </div>
    );
}
