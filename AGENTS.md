# JavaClaw - Java Edition of OpenClaw

This project represents a Java version of OpenClaw. OpenClaw is an open-source, personal AI assistant designed to run on your own devices. It acts as a control plane (Gateway) for an assistant that can interact across multiple communication channels.

### Key Capabilities of OpenClaw
- **Multi-Channel Integration:** Supports platforms like WhatsApp, Telegram, Slack, Discord, Google Chat, Signal, iMessage, Microsoft Teams, Matrix, and more.
- **Background Daemon:** Runs as a background service to handle tasks and messages autonomously.
- **Extensible Skills:** Modular capabilities that can be added to enhance the assistant's functionality.
- **Local Control:** Designed for privacy and control, running on your own hardware.

---

## Technology Stack
- **Java 25**, Spring Boot 4.0.3, Spring Modulith 2.0.3
- **Job Scheduling:** JobRunr 8.5.0 — background jobs, dashboard on `:8081`
- **LLM Integration:** Spring AI 2.0.0-SNAPSHOT (OpenAI, Anthropic, Ollama)
- **MCP:** Spring AI MCP Client (Model Context Protocol)
- **Agent Framework:** Spring AI Agent Utils (Anthropic agent framework)
- **Database:** H2 (embedded)
- **Templating:** Pebble 4.1.1
- **Discord:** JDA 6.1.1 (Gateway / WebSocket)
- **Telegram:** Telegrambots 9.4.0 (long-polling)

---

## Module Structure

```
root
├── base/           ← Core: agent, tasks, tools, channels, config
├── app/            ← Spring Boot entry point, onboarding UI, web routes, chat channel
└── plugins/
    ├── brave/      ← Brave web search integration
    ├── discord/    ← Discord Gateway channel
    ├── playwright/ ← Browser automation tools
    └── telegram/   ← Telegram long-poll channel
```

`app` depends on `base` plus plugin modules. `ChatChannel` lives inside `app/`.

---

## Java Implementation Strategy (JavaClaw)

### Task Management
- Tasks are stored as Markdown files in the `workspace/tasks` folder.
- Path format for normal tasks: `yyyy-MM-dd/<HHmmss>-<name>.md`.
- Path format for recurring tasks: `recurring/<name>.md`.
- **States:** `todo`, `in_progress`, `completed`, `awaiting_human_input` — stored in YAML frontmatter (not in the filename).
- Task file format: YAML frontmatter with `task`, `createdAt`, `status`, `description` fields.
- The task `id` is the absolute file path, set by `FileSystemTaskRepository` on first save.

### Core Task Flow
```
User/Agent → TaskManager.create()
  → Task written as .md file via TaskRepository
  → JobRunr enqueues TaskHandler.executeTask(taskId)
  → TaskHandler: marks in_progress → prompts Agent → marks completed/awaiting_human_input
  → On error: resets status back to todo (retried up to 3 times by JobRunr)
```

### Core Components
- **`TaskManager`** (`base/src/main/java/ai/javaclaw/tasks/TaskManager.java`): Creates, schedules (immediate, specific-time, or cron), and manages recurring tasks. Saves via `TaskRepository`, then enqueues to JobRunr by task ID.
- **`Task`** (`base/.../tasks/Task.java`): Immutable value class. Fields: `id` (absolute file path), `name`, `createdAt`, `status`, `description`. Mutation via `withStatus()` / `withFeedback()`.
- **`TaskRepository`** (`base/.../tasks/TaskRepository.java`): Interface for task persistence — `save(Task)`, `getTaskById(String)`, `getTasks(LocalDate, Status)`, `save(RecurringTask)`, `getRecurringTaskById(String)`.
- **`FileSystemTaskRepository`** (`base/.../tasks/FileSystemTaskRepository.java`): Implements `TaskRepository`; handles all file I/O, YAML frontmatter serialization/deserialization, directory management, and filename sanitization.
- **`TaskHandler`** (`base/.../tasks/TaskHandler.java`): `@Job(retries=3)`-annotated; executes task via agent prompt; returns `TaskResult(Status newStatus, String feedback)` record (nested inside `TaskHandler`). Resets to `todo` on exception.
- **`RecurringTask`** (`base/.../tasks/RecurringTask.java`): Immutable value class for recurring task templates stored in `workspace/tasks/recurring/`.
- **`RecurringTaskHandler`** (`base/.../tasks/RecurringTaskHandler.java`): JobRunr worker spawning normal tasks from recurring templates on cron schedule.
- **`TaskNotFoundException`** (`base/.../tasks/TaskNotFoundException.java`): Thrown when a task file cannot be found or read by its ID.

### Workspace
- `workspace/AGENT.md`: System instructions + user-specific information (editable during onboarding).
- `workspace/AGENT-ORIGINAL.md`: Template / backup of original AGENT.md.
- `workspace/INFO.md`: Environment context auto-injected into every prompt.
- `workspace/context/`: Agent memory and context storage (e.g. `jobrunr.md`).
- `workspace/skills/`: Extensible skill files (`SKILL.md` per skill, loaded dynamically by `SkillsTool`).
- `workspace/tasks/`: Date-bucketed task files + `recurring/` sub-folder.

---

## Agent System

**`DefaultAgent`** (`base/src/main/java/ai/javaclaw/agent/DefaultAgent.java`) wraps Spring AI's `ChatClient`.

**Prompt construction** (`JavaClawConfiguration.java`):
- System prompt = `workspace/AGENT.md` + `workspace/INFO.md`
- **Advisors**: `SimpleLoggerAdvisor`, `ToolCallAdvisor`, `MessageChatMemoryAdvisor` (chat history)

**Default Tools** always injected:
| Tool | Purpose |
|---|---|
| `TaskTool` | `createTask`, `scheduleTask`, `scheduleRecurringTask` |
| `CheckListTool` | Multi-step structured tracking (one `in_progress` at a time) |
| `ShellTools` | Bash execution |
| `FileSystemTools` | Read/write/edit files |
| `SmartWebFetchTool` | Intelligent web scraping |
| `SkillsTool` | Loads `SKILL.md` files from `workspace/skills/` |
| `McpTool` | Runtime MCP server management |
| `MCP Tools` | `SyncMcpToolCallbackProvider` |
| `BraveWebSearchTool` | 15 results (only if Brave API key configured) |

**Supported LLM Providers** (`SupportedProvider.java`):
- `OLLAMA` — local, no API key required
- `OPENAI` — GPT-5.4
- `ANTHROPIC` — Claude Sonnet 4.6

---

## Channel Architecture

```
Incoming message → ChannelMessageReceivedEvent (channel name, message text)
  → ChannelRegistry routes to Agent
  → Agent.respondTo() → Channel.sendMessage()
```

- **`ChannelRegistry`**: Registers channels, tracks last-active channel so background task replies are routed correctly.
- **`DiscordChannel`**: JDA `ListenerAdapter`; accepts DMs from the configured user and guild messages only when the bot is mentioned.
- **`TelegramChannel`**: `SpringLongPollingBot`; filters by `allowedUsername`; stores `chatId` for routing background replies.
- **`ChatChannel`**: WebSocket-first delivery (`setWsSession()`/`clearWsSession()`); falls back to buffering replies in `ConcurrentLinkedQueue` exposed via `drainPendingMessages()` REST endpoint when no WebSocket session is active.

---

## Configuration Management

- **`ConfigurationManager`** (`base/src/main/java/ai/javaclaw/configuration/ConfigurationManager.java`): Updates nested YAML key-value paths in `application.yaml` via SnakeYAML. Publishes `ConfigurationChangedEvent` on update.
- Runtime config file: `app/src/main/resources/application.yaml` (read at startup; mutated by onboarding).
- Key config paths:
  - `agent.onboarding.completed` — set to `true` after onboarding
  - `agent.workspace` — path to workspace root (`file:./workspace/`)
  - `spring.ai.model.chat` — overridden to selected provider/model during onboarding
  - `jobrunr.background-job-server.worker-count: 1`
  - `jobrunr.dashboard.port: 8081`

---

## Tools and Capabilities
- **File System:** Read, write, and edit files.
- **Shell:** Execute bash commands.
- **Web:** Search (Brave API) and smart web fetching.
- **MCP:** Support for Model Context Protocol tools (via `SyncMcpToolCallbackProvider`).
- **Skills:** Custom modular skills loaded from `workspace/skills/` at runtime.
- **Channels:** Chat, Telegram, and Discord are implemented.

---

## Frontend Notes
- **Templating (https://pebbletemplates.io/):** Server-rendered HTML uses Pebble templates (suffix `.html.peb`) under `app/src/main/resources/templates/`. Base template: `templates/base.html.peb`.
- **Bulma (https://bulma.io/documentation):** Bulma v1.0.4 (CDN) is used for all web layout — CSS-only, mobile-first, semantic classes + modifiers like `is-primary`, `is-loading`, `is-danger`. Bulma v1 relies heavily on CSS variables. Themes are collections of CSS variables, so branding and light/dark adjustments can be done without rewriting component markup. Use Bulma layout primitives (`section`, `container`, `columns`, `card`, `message`, `progress`, `hero`) and helper classes instead of bespoke layout CSS where possible.
- **htmx v2.0.8 (https://htmx.org/docs/):** htmx is a strong fit for this app because it keeps the interaction model server-driven: the server returns HTML fragments, not JSON, and htmx swaps them into the DOM. We are using `hx-boost` which "boosts" normal anchors and form tags to use AJAX instead (preventing reloading of css and js). This has the nice fallback that, if the user does not have javascript enabled, the site will continue to work. Both Bulma and htmx are already included in `base.html.peb`.

### Onboarding UI
Entry point: `GET /index` → `IndexController.java` (redirects to `/onboarding/`) → `OnboardingController.java`. Core onboarding flow:
1. Welcome
2. Provider selection (Ollama / OpenAI / Anthropic)
3. Credentials (API key + model)
4. `AGENT.md` editor (system prompt customization)
5. MCP servers configuration (optional)
6. Optional channel/tool plugin steps (for example Telegram and Discord)
7. Complete summary

Templates live under `templates/onboarding/`, with plugin steps contributed from their own modules. Saves config via `ConfigurationManager.updateProperty()`.

---

## Key Architectural Patterns

| Pattern | Usage |
|---|---|
| **Event-Driven** | `ChannelMessageReceivedEvent`, `ConfigurationChangedEvent`, JobRunr background dispatch |
| **Template Method** | `AbstractTask` subclassed by `Task`, `RecurringTask` |
| **Strategy** | Multiple `Channel` implementations (Discord, Telegram, Chat) |
| **Record Types** | `TaskResult`, `CheckListItem` — structured LLM response types |
| **Markdown as State** | Tasks stored as `.md` files — queryable, diffable, human-readable |
| **Single Agent Instance** | `DefaultAgent` wraps `ChatClient`; all prompts routed through it |
| **Server-Driven HTML** | htmx + Pebble; progressive enhancement, works without JS |

---

## Tests

- `base/src/test/` — `TaskManagerTest`: task creation, file naming, JobRunr integration (in-memory storage + background server).
- `plugins/discord/src/test/` — `DiscordChannelTest`, `DiscordOnboardingProviderTest`: authorized Discord flow + onboarding config handling.
- `plugins/telegram/src/test/` — `TelegramChannelTest`: unauthorized user rejection, authorized message flow (mocked).
- `app/src/test/` — `OnboardingControllerTest`: session-based workflow; `JavaClawApplicationTests`: full Spring context load with Testcontainers.
