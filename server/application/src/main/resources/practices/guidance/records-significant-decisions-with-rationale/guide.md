## How to do it

- Before you ask for a review, check whether the change makes a significant decision. Only these kinds count. A change to stored data or a schema counts. So do a change to a public API or a message format, and an auth or security mechanism. A new mechanism that other code plugs into counts too, and so does removing a feature with stored data.
- If it does, say why in the title, the description, or a commit message. A decision record that the change adds or links works too.
- Name the need the decision serves or the constraint that forced it. One clause is enough. For example, instead of “Added an attachments table”, write “Store attachments in a table, because they must survive a restart”.
- If the change makes more than one significant decision, give a reason for each one.

## When it does not apply

- The change makes no architectural decision. A new screen, a cache, or a helper is not one by itself.
- The change only touches tests, formatting, or docs.
- The change renames something without changing a contract, such as stored data, a public API or a message format.
- The change only adds a dependency or changes its version.
- The change tunes a constant or a threshold inside the code.
- The change only touches generated code. Only lines that a person wrote count.
- The change is empty.

It still applies when you write a migration by hand.

## Common mistakes

- Thinking you need a long document that lists every option you turned down. You do not. Instead, write one clause that names the need or the constraint.
- Repeating what the code does as the reason. “Added an audit log step that runs on every request, so every request is now logged” says what changed. Instead, say what need the logging serves.
- Giving only the goal of the feature. “Users can attach files” does not say why attachments are stored. Instead, connect the goal to the decision.
- Explaining the decision only in a reply to a review comment. A future maintainer will not look there. Instead, put the reason in the change itself.

## Sources

- [Documenting architecture decisions](https://www.cognitect.com/blog/2011/11/15/documenting-architecture-decisions), Michael Nygard, 2011. A short record of the reasons, kept with the code, stops a team from accepting or changing a decision blindly.
- [Maintain an architecture decision record](https://learn.microsoft.com/en-us/azure/well-architected/architect-role/architecture-decision-record), Microsoft Azure Well-Architected Framework. A decision recorded without its reason loses value, because readers cannot judge if it still holds.
- [A survey of architecture design rationale](https://doi.org/10.1016/j.jss.2006.04.029), Tang, Babar, Gorton and Han, 2006. In 81 responses, practitioners said that design rationale matters, but they reported barriers to writing it down.
