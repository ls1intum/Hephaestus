/* eslint-disable react-refresh/only-export-components */
import { createRoot } from "react-dom/client";
import { StrictMode } from "react";
import { KcPage } from "./kc.gen";

// The following block can be uncommented to test a specific page with `yarn dev`
// Don't forget to comment back or your bundle size will increase
// import { getKcContextMock } from "./login/KcPageStory";

// if (import.meta.env.DEV) {
//     window.kcContext = getKcContextMock({
//         pageId: "login.ftl",
//         overrides: {
//             message: {
//                 type: "error",
//                 summary: "This is an error message",
//             },
//             social: {
//                 providers: [
//                     {
//                         loginUrl: "http://localhost:8080/auth/realms/hephaestus/broker/github/endpoint",
//                         alias: "github",
//                         providerId: "github",
//                         displayName: "GitHub",
//                         iconClasses: "fa fa-github",
//                     },
//                     {
//                         loginUrl: "http://localhost:8080/auth/realms/hephaestus/broker/gitlab-lrz/endpoint",
//                         alias: "gitlab",
//                         providerId: "gitlab-lrz",
//                         displayName: "GitLab LRZ",
//                         iconClasses: "", // None since it is OpenID Connect
//                     }
//                 ],
//             },
//         }
//     });
// }

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        {!window.kcContext ? (
            <h1>No Keycloak Context</h1>
        ) : (
            <KcPage kcContext={window.kcContext} />
        )}
    </StrictMode>
);
