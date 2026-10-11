## How to do it

- Move logic that you would paste again into one named function, constant, loop or lookup table.
- Pass in what differs between the copies, such as a value, a name or a type.
- For example, instead of copying a tax calculation into two places, write one `addTax(sum)` and call it twice.
- When `if` or `case` branches repeat the same steps, set the one constant that differs in each branch. Then run the steps once after them.
- Name the shared part after the rule that it holds.

## When it does not apply

- The change only updates documentation, diagrams or images.
- Every added line is generated code, copied-in third-party (vendored) code or a test fixture.

It still applies when hand-written configuration repeats a rule that your team maintains.

## Common mistakes

- Judging by length or by looks. Length alone does not make two blocks copies, and neither does a similar shape. Instead, ask if both blocks express the same rule. If they would change for different reasons, keep them apart.
- Merging short idioms that only look alike. Some idioms repeat a few lines that the language or framework asks for. Examples are guard clauses (early `return` checks), field assignments, equality and hash methods, one-line `switch` arms and route registrations. Test cases and fixtures look alike on purpose too. Leave them as they are.
- Growing a shared helper with flags. If each new caller adds a parameter and an `if` to the helper, the callers no longer share one rule. Instead, put the code back into each caller, and then extract only the part that they truly share.

## Sources

- [Avoiding repetition](https://martinfowler.com/ieeeSoftware/repetition.pdf), Martin Fowler, IEEE Software, 2001 (DOI 10.1109/52.903160). Find what is the same and what varies, move the same part into one place, and pass in the difference. Some duplication is only a coincidence.
- [Do code clones matter?](https://arxiv.org/abs/1701.05472), Juergens, Deissenboeck, Hummel and Wagner, ICSE 2009 (DOI 10.1109/ICSE.2009.5070547). In five industrial and open-source systems, copies of code were often changed in only some places. Nearly every second unplanned change of this kind was a fault.
- [The wrong abstraction](https://sandimetz.com/blog/2016/1/20/the-wrong-abstraction), Sandi Metz, 2016. A shared helper that gains a parameter and a condition for each caller is the wrong abstraction. Put the code back into the callers and extract it again.
