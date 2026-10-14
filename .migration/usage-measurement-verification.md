#### 🔴 Upgrade usage verification with all writers stopped

**Operators:** Follow the [stopped-writer upgrade procedure](https://docs.hephaestus.build/admin/native-hibernate-json-cutover).
Do not mix runtime-role versions during this cutover.
An older repricer does not honor the new measurement-verification decision and can price usage that must remain unpriced.

The migration records historical verification as `UNKNOWN` without changing existing token counts, rates, or stored costs.
Historical `UNKNOWN` records that are already unpriced cannot be repaired automatically by adding rates.
Their existing current-month cap holds remain.
Previously priced amounts stay in totals; they do not gain a new verification claim.

New incomparable measurements are `UNVERIFIABLE` and have no confirmed cost, even when rates exist.
Adding a rate repairs only verified usage with positive billable tokens.
Do not assign historical verification through manual database edits.
This cutover does not recover provider usage that arrives after terminal accounting.
