# Contributing

1. One concern per pull request.
2. New tools implement `Tool` via `BaseTool`, declare a `ToolSpec`, and register in the
   matching `register*Tools()` function.
3. New agents are data only: add an `AgentSpec` to `AgentCatalog`.
4. Run `gradle test` before opening the PR. The engine has no Android dependencies, so
   logic tests belong in `app/src/test`.
5. No analytics, no telemetry, no new third-party SDK that phones home.
