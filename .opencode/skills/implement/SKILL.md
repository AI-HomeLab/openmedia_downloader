---
name: implement
description: "Implement a piece of work based on a spec or set of tickets."
disable-model-invocation: true
---

Implement the work described by the user in the spec or tickets.

Tests are lightweight in this repo — do not default to writing new tests. Keep existing tests/checks green and verify the work runs. Only use skill `tdd` if the user explicitly asked for test-first work.

Run existing checks and the existing test suite once at the end; fix any regressions.

Once done, use skill `code-review` to review the work.

Commit your work to the current branch.
