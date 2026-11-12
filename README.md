<p align="center">
  <img src="docs/banner.png" alt="Apex AI" width="100%">
</p>

<p align="center">
  <a href="https://github.com/1234567890933/ApexAI/actions/workflows/build-apk.yml"><img alt="Build" src="https://github.com/1234567890933/ApexAI/actions/workflows/build-apk.yml/badge.svg"></a>
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-24-7C5CFF">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-34-22D3EE">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-F472B6">
</p>

# Apex AI

An open-source Android client for large language models with a real agent runtime:
a streaming chat surface, an autonomous tool loop, and manager/worker teams — in one
APK, with no backend of its own.

You bring a provider key (Google Gemini, OpenAI, OpenRouter, Groq, or any
OpenAI-compatible endpoint including a LAN Ollama). Apex stores it in the Android
Keystore, streams tokens over SSE, and lets the model call tools on the device.

---

## What actually works

| Area | Implementation |
|---|---|
| Streaming | Hand-written SSE parser over OkHttp; token-level rendering with a 45 ms coalescing buffer |
| Providers | Gemini `streamGenerateContent` + any OpenAI-compatible `/chat/completions` |
| Agent modes | `SINGLE` (one shot), `OPEN_ENDED` (tool loop), `TEAM` (manager + specialists) |
| Tool protocol | `<tool name="id">{json}</tool>` with a streaming filter that never shows markup |
| Tools | 22 built in across compute, text, network, security, files, system |
| Credentials | `EncryptedSharedPreferences` on an AES-256-GCM Keystore master key |
| History | Atomic JSON persistence, 40 sessions, no Room/KSP |
| Voice | Platform TTS, offline, per-message speaker button |
| UI | Material 3 + custom glass panels, aurora depth layer, spring motion, haptics |

## Agent presets

| Agent | Mode | What it does |
|---|---|---|
| **Apex** | autonomous | Everyday assistant with a broad tool set |
| **Scout** | autonomous | Research: gathers evidence before answering |
| **Forge** | autonomous | Writes and repairs code |
| **Ledger** | autonomous | Quantitative work, Indian units, tool-verified arithmetic |
| **Quill** | single | Editing and prose |
| **Compass** | single | Turns a goal into an ordered plan |
| **Sentinel** | autonomous | Static security review of packages you own |
| **Prism** | autonomous | Reverse-engineering study partner |
| **Team · Ship** | team | Architect → Engineer → Reviewer → QA |
| **Team · Probe** | team | Scout → Analyst → Editor |

## The tool suite

Compute: `calc`, `uuid`, `now`, `device_info`
Text: `text_stats`, `word_freq`, `regex_test`, `case_convert`, `diff`, `base64`, `json_format`
Network: `dns_lookup`, `http_probe`, `port_probe`, `url_parse`
Security: `hash_text`, `apk_inspect`, `manifest_peek`, `dex_strings`, `file_entropy`
Files: `fs_list`, `fs_read`, `fs_write`, `fs_search`, `fs_delete`

The security tools are **static analysis only**: they read bytes you imported into the
app sandbox. There is no injection, no process tampering, no exploit payload, and no
network access in that category. Use them on software you own or are authorised to
inspect.

## Build

Locally:

```bash
git clone https://github.com/1234567890933/ApexAI.git
cd ApexAI
gradle assembleDebug        # or ./gradlew after `gradle wrapper`
```

In CI: push to `main`, or run **Actions → Build Apex AI APK → Run workflow**. The
workflow runs the unit tests, assembles debug and release APKs (release signed with a
throwaway keystore generated in the runner), and uploads them as `apex-ai-apks`.
Pushing a `v*` tag also attaches the APKs to a GitHub Release.

> Release builds are signed with a CI-generated key. For a build you intend to
> distribute, supply your own keystore through `APEX_KEYSTORE`, `APEX_STORE_PASS`,
> `APEX_KEY_ALIAS`, `APEX_KEY_PASS`.

## Architecture

```
com.apex.ai
├── core/       vault · DI container · logging · redaction · haptics · outcome
├── llm/        SSE engine · Gemini · OpenAI-compatible · provider registry
├── agents/     specs · runtime loop · team orchestrator · prompt builder
├── tools/      registry · protocol parser · 22 built-in tools
├── sandbox/    expression engine · jailed filesystem · import bridge
├── data/       session models · atomic JSON store
├── ui/         chat · agents · settings · toolkit · widgets · motion
└── speech/     platform TTS wrapper
```

Data flow for one turn:

```
prompt → ChatViewModel → Orchestrator
           ├── SINGLE/OPEN_ENDED → AgentRuntime (think → stream → tools → observe → repeat)
           └── TEAM             → TeamOrchestrator (plan → workers → synthesise)
        → Flow<AgentEvent> → MainActivity → ChatAdapter
```

Everything the UI needs arrives as a `Flow<AgentEvent>`; the UI holds no engine state,
so cancellation is just cancelling the collector.

## Security posture

- Keys never leave the device except in the `Authorization` header of the provider call.
- `Redactor` scrubs credentials from the in-app engine log before it is displayed.
- The sandbox filesystem canonicalises every path and rejects anything resolving
  outside `filesDir/sandbox`.
- Backups exclude the vault (`data_extraction_rules.xml`).
- No analytics, no telemetry, no third-party SDKs.

## Limits, stated honestly

- This is a **client**: it needs a provider key and network access to answer.
- Team runs are sequential, not parallel — deliberate, to avoid tripling token
  throughput on a phone.
- `manifest_peek` reads the binary XML string pool rather than fully decoding
  resources; it is a triage view, not a substitute for `aapt2 dump`.
- The step budget (default 8) caps autonomous runs so a confused model cannot burn
  your quota.

## License

MIT — see [LICENSE](LICENSE).

---

Built and verified by CI: `gradle test` → `assembleDebug` → `assembleRelease`.
The latest signed APKs are attached to the [releases page](../../releases).
