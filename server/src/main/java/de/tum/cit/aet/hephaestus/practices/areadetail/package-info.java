/**
 * Read models behind one practice area's detail page: the direction it is heading and the review moments
 * that produced its feedback. Both halves are served by a single controller under
 * {@code /practice-areas/{areaSlug}}, which is why they share a package rather than sitting with the
 * machinery they read from.
 *
 * <p>The trend QUERY service lives here; the trend MATHEMATICS does not. Posterior estimation, bundling and
 * classification stay in {@code practices.observation.trend} — this package only asks that module a
 * question on behalf of a screen, scoped to one area. Naming the package after the review history alone
 * hid that second half.
 *
 * <p>Deliberately not a named interface: nothing outside reads these types. Everything the rest of the
 * application needs about an area's standing comes through {@code practices.observation}, and keeping this
 * package closed is what lets a screen's shape change without a module boundary moving.
 */
package de.tum.cit.aet.hephaestus.practices.areadetail;
