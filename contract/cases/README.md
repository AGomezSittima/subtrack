# Shared business-rule cases

One JSON file per business-rule table in `docs/srs.md` (for example `br-02-month-end-clamping.json`), each a list of input → expected output cases. The pytest and JUnit 5 suites both load these files and run them against their billing core, so the two backends can't drift apart.

Adding or changing a business rule starts here: add or change its cases first, then make both backends pass.

No cases yet: the billing core arrives with the first billing feature.
