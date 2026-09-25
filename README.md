# QuizForge Desktop V2

Independent Java 21 Maven desktop project. It does not use QuizForge V1 code or start a web server.

## Module boundaries

| Module | Responsibility | Direct project dependencies |
| --- | --- | --- |
| `quizforge-extension-api` | Vendor neutral AI and document contracts | None |
| `quizforge-core` | Workspace, Material and StandardDocument models, services and ports | `quizforge-extension-api` |
| `quizforge-default-extensions` | DeepSeek and Standard Markdown v1 processing and validation | `quizforge-extension-api` |
| `quizforge-infrastructure` | SQLite, Flyway, local files and Windows DPAPI credentials | `quizforge-core` |
| `quizforge-desktop-app` | JavaFX UI and Spring composition root | All four modules |

## Build and run

Use Maven with JDK 21 from this directory:

```powershell
mvn test
mvn install -DskipTests
mvn -pl quizforge-desktop-app javafx:run
```

The app creates `%USERPROFILE%\.quizforge\quizforge.db`. Imported materials are stored
under `workspaces\{workspace-id}\materials\`. Validated Standard Documents are stored
at `workspaces\{workspace-id}\document\study.md`. SQLite stores metadata and Material
provenance. AI provider settings contain a credential reference; API keys are encrypted
with Windows DPAPI in `secrets\` and never stored in SQLite.

Override the data directory for an isolated run with the JVM property `quizforge.dataDir`.
For example, in PowerShell:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Dquizforge.dataDir=C:\temp\quizforge-demo'
mvn -pl quizforge-desktop-app javafx:run
Remove-Item Env:JAVA_TOOL_OPTIONS
```

In AI Settings, save DeepSeek Base URL, Model and your own API key, then use Test Connection.
Defaults are `https://api.deepseek.com` and `deepseek-v4-flash`. Network calls run in
background JavaFX tasks. Candidate documents are saved only after local Standard Markdown
v1 validation. A failed regeneration keeps the previous document.

`quizforge.material.maxBytes` defaults to 10 MiB per imported Markdown material.
`quizforge.document.maxInputChars` defaults to 100,000 characters across selected Material
names and content. Oversized inputs are rejected without truncation.

`mvn test` uses fake providers and a local mock HTTP server; it never contacts the live
DeepSeek API. Live acceptance requires a user supplied key entered locally in AI Settings.
