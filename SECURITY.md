# Security policy

## Reporting a vulnerability

Please report vulnerabilities privately through
[GitHub security advisories](https://github.com/Fayaz-Deen/commercetools-mcp-server/security/advisories/new),
not as a public issue. Include steps to reproduce and the affected version.

You can expect an acknowledgement within a few days. Fixes are released as a new version and noted in the
release notes.

## Supported versions

Only the latest release receives security fixes.

## Scope notes

- The server is read-only unless `CT_MCP_ALLOW_WRITES=true` is set.
- The optional HTTP transport has no authentication and binds to `127.0.0.1` by default. Do not expose it
  to a network without putting an authenticating proxy in front of it.
- Never paste real credentials or customer data into issues, logs or pull requests.
