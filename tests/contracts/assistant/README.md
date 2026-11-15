# Assistant compatibility contract

`contract.json` freezes the frontend-visible behavior of the original Spring Boot assistant before
its Python migration. The replacement must preserve endpoint paths, authentication, SSE event
names, conversation ownership behavior, and the initial tool names.

Validate the fixture with `node tests/contracts/assistant/verify-contract.mjs`.

Runtime behavior remains covered by the original Java controller/tool tests until the Python
contract suite replaces them. The frontend assistant tests are the consumer-side contract.
