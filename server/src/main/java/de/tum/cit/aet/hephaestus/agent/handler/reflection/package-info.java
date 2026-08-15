/**
 * The producer for the {@code REFLECTION} feedback lane.
 *
 * <p>Three lanes carry feedback, and they are three levels of feedback, not three copies of one message
 * (Hattie &amp; Timperley; the levels are written down in {@code agent/mentor/system.md}):
 *
 * <ul>
 *   <li><b>In context</b> ({@code IN_CONTEXT}) — the <em>task</em> level. What is wrong here, in this
 *       diff, at this line. Evidence is a quoted line; the next step is one edit in this change.
 *   <li><b>Reflection</b> ({@code REFLECTION}, this package) — the <em>process</em> level. The habit
 *       that recurs across this person's work, and what to do differently next time. Evidence is a set
 *       of artifacts sharing a practice; the next step is a habit, executable next time and never
 *       actionable on any one diff — a next step you could do right now is a task-level note wearing a
 *       costume.
 *   <li><b>The mentor conversation</b> ({@code CONVERSATION}) — the <em>self-regulation</em> level. The
 *       same evidence, held back, turned into a question the developer answers about their own work.
 * </ul>
 *
 * <p>Two rules survive every lane and are enforced by the composer's prompt rather than by code, because
 * they are properties of wording: <b>no self-level praise</b> — feedback aimed at the person is the
 * least effective register and sometimes a harmful one — and <b>evidence plus a next step, always</b>.
 *
 * <p><b>Separation of measurement from intervention.</b> An observation is the measurement; feedback is
 * the intervention. Nothing in this package writes prose. The words come from a composition turn that
 * runs after the review's measurements are final, in its own session, with a tool that structurally
 * cannot emit a presence, an assessment or a severity. This package decides only where those words may
 * land and what they are evidenced by, and it resolves the evidence itself rather than believing the
 * model's account of it.
 *
 * <p><b>The reflection surface is private.</b> A REFLECTION body is the first system-authored text
 * about a named person that is not also visible to them somewhere else, and in a course deployment the
 * workspace admin is the instructor. The operator surfaces therefore show that a REFLECTION unit exists,
 * its state and its reason, and never its text ({@code ReviewFeedbackQueryService}). That direction is
 * reversible; the other one is not.
 */
package de.tum.cit.aet.hephaestus.agent.handler.reflection;
