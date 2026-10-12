---
"hephaestus": patch
---

Practice reviews admin fixes:

- **See all** on the queue card opens the reviews that run, wait or are on hold, so the list holds the reviews the card counts.
- A requested review refreshes the overview, the queue and the review time at once, not only the lists.
- The practice summary above the Observations list leaves replaced feedback out of **Feedback delivered**, as the Practices table does.
- **Request another review** on the sheet of a review is disabled while another review of the same work is queued or running.
- The (i) of **Track** says that it narrows the tabs to the records of the same review or work, not to the one record.
- The **Reason not recorded** row of **Withheld by reason** comes from the same read as the reasons, so the two never disagree while reviews run. The count of withheld feedback by reason in the admin API includes that feedback as one count with no reason.
- The verdict of a trace and the line under it now tell the same merge time for feedback withheld as already merged.
