import { createContext } from "react";

/**
 * Whether the practice form around a banner holds unsaved changes. `PracticeDefinitionForm` owns the
 * draft and provides it to `beforeFields`, a slot its host fills, so no prop path reaches from the
 * draft to the banner. Outside the form there is no draft to lose.
 */
export const UnsavedPracticeDraft = createContext(false);
