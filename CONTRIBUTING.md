# Contributing

Thanks for your interest in improving commercetools-mcp-server! Bug reports, new tools and documentation
fixes are all welcome.

## Getting started

Requirements: JDK 21 or newer. Maven is not needed; use the wrapper.

```bash
git clone https://github.com/Fayaz-Deen/commercetools-mcp-server.git
cd commercetools-mcp-server
./mvnw verify
```

`./mvnw verify` compiles, runs all unit tests and builds `target/commercetools-mcp-server.jar`. No
commercetools project is needed for the tests: the SDK's HTTP client is mocked and the real SDK request
builders and JSON parsing run against canned responses in `src/test/resources/fixtures`.

## Adding a tool

1. Pick the class that fits (`CatalogTools`, `OrderTools`, `CustomerTools`) or add a new one and register it
   in `McpServerConfig`.
2. Annotate a public method with `@Tool(name = "snake_case_name", description = "...")` and each parameter
   with `@ToolParam`. The description is what the AI reads, so say what the tool returns.
3. Validate every input with `Inputs` before calling commercetools. Put user values into query predicates
   only through `withPredicateVar`, never by string concatenation.
4. Wrap the SDK call in `CtCalls.call(what, scope, ...)` so errors are short and name the missing scope.
5. Return a record from `Views` with only the fields an assistant needs. Mask personal data.
6. Add tests next to the existing ones: happy path, request URL or predicate, invalid input, not found.
7. Add the tool to the tool reference table in `README.md`.

Tools that change data must go in a class registered only when `CT_MCP_ALLOW_WRITES=true`
(see `CartWriteTools`), and their description must start with `WRITE:`.

## Pull requests

- Keep each pull request focused on one change.
- Use [Conventional Commits](https://www.conventionalcommits.org/) for commit messages, for example
  `feat: add get_shipping_methods tool` or `fix: handle missing variant prices`.
- Make sure `./mvnw verify` passes.
- Never commit real credentials, project data or customer data. Fixtures must be synthetic.

## Code style

- Java 21, four-space indentation, records for value types.
- Small classes and methods; prefer clear names over comments.
- Match the style of the surrounding code.

## Reporting bugs and ideas

Use the issue templates. For security problems see [SECURITY.md](SECURITY.md).
