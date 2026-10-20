# CostPhone Engine 0.1.2

CostPhone Engine is an **additive, opt-in orchestration layer** above the existing amctl CLI. It does not replace the Android APK, Accessibility service, Shizuku transport, updater, or existing phone-control skill. It uses only Python's standard library and invokes amctl with argument arrays; it never evaluates task text as shell commands.

## What this first version includes

- Local CLI: inspect, launch-and-verify, validate a JSON plan, execute a staged plan, or start MCP.
- MCP stdio server for agents such as OpenCode: phone_inspect, phone_launch, phone_validate_plan, phone_run_plan. The MCP `phone_run_plan` tool accepts `dry_run: true` to preview without phone I/O.
- Each plan stage checks the foreground package and visible text before it acts, then checks the expected screen afterwards. If a stage has multiple actions, every action after the first must include its own requires text so the engine re-reads the screen before continuing.
- Sensitive button actions (for example Send, Delete, Pay, Publish, Checkout and Enter) are blocked by default. There is no automatic action retry.
- No Python packages, APK rebuild, or modification of existing amctl commands is required.

The engine is general-purpose across Android apps and workflows when the target exposes usable accessibility data and the required permissions are granted. It is not a promise that every app or task can be automated completely; apps that expose little accessibility data may need a future visual-perception adapter. Applications that do not expose useful accessibility data may need a future visual-perception adapter. The engine cannot prove facts that the app does not expose.

## Install separately (does not change the existing updater)

From Termux, run:

~~~sh
curl -fsSL "https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/engine/costphone-engine-v0/termux/install-costphone-engine.sh" -o "$HOME/install-costphone-engine.sh"
bash "$HOME/install-costphone-engine.sh"
~~~

The installer downloads into a temporary directory, compiles and runs the offline tests, then installs to ~/.local/share/costphone-engine/ and ~/bin/. It adds a separate costphone-engine skill directory. It does **not** edit opencode.json, AGENTS.md, the existing phone-control skill, or the existing updater.

After installation, basic commands are:

~~~sh
costphone-engine --version
costphone-engine inspect
costphone-engine launch com.android.settings
~~~

The inspect command is intentionally expected to fail closed while Termux is the foreground app or CostPhone cannot identify the visible application. Open the target application before asking the engine to inspect its screen. The launch command opens the application and then independently checks the observed foreground package.

## Register the MCP server in OpenCode

Add the following entry under the existing mcp object in ~/.config/opencode/opencode.json (create mcp only if it does not exist). Merge it into your current configuration; **do not replace the whole file**. Replace the illustrative paths with the exact paths shown by running echo "$HOME" on your phone.

~~~json
"costphone-engine": {
  "type": "local",
  "command": ["/data/data/com.termux/files/home/bin/costphone-mcp"],
  "enabled": true,
  "timeout": 120000
}
~~~

The entry's key can be costphone-engine. A 120000 ms client timeout accommodates slower Android commands; the engine command timeout is 30 seconds by default. Restart OpenCode after saving the configuration. The server uses MCP over stdio; it writes protocol data to stdout and no diagnostic banners.

## Use it with other AI clients or without OpenCode

The engine is not tied to OpenCode. Any AI client that supports a local MCP server over stdio can connect to `~/bin/costphone-mcp` using the same command and arguments. Merge the server entry into that client's MCP configuration, using the actual Termux home path and a sufficiently long client timeout. The client must be able to launch a local process on the same device; a cloud-only AI chat cannot directly control the phone just by knowing this repository URL.

For non-AI or custom integrations, use the CLI directly: `costphone-engine inspect`, `costphone-engine launch PACKAGE`, `costphone-engine validate plan.json`, and `costphone-engine run plan.json`. An agent or wrapper can generate the JSON plan and call the CLI, but should not treat CLI text as proof of an external effect. The engine is a deterministic executor; the AI client supplies task interpretation and plan construction.

## Sensitive actions

External effects are disabled by default. Register the MCP server without any override first. Only when a task explicitly requires an external effect and you understand the consequences, add this environment setting to that MCP entry:

~~~json
"environment": {
  "COSTPHONE_ALLOW_EXTERNAL_EFFECTS": "1"
}
~~~

This switch enables the policy category; it does **not** skip package checks or context checks. A sensitive action must declare requires strings that are re-read immediately before the action. Plans are schema-validated in full before the first device command. If the context is missing or ambiguous, execution stops. Do not enable this switch globally just to silence a block.

## Plan format

A plan is JSON, schema version 1. Keep steps short and explicit. Every step requires an exact package, positive visible-text assertions before actions, and expected visible text afterwards. Optional launch opens and verifies a package before checking that stage's preconditions.

~~~json
{
  "version": 1,
  "name": "Open a settings section",
  "steps": [
    {
      "launch": "com.android.settings",
      "expect": {
        "package": "com.android.settings",
        "contains": ["Settings"]
      },
      "actions": [
        {"kind": "click_text", "value": "Network"}
      ],
      "after": {
        "package": "com.android.settings",
        "contains": ["Network"]
      }
    }
  ]
}
~~~

Allowed action kinds: click_text, click_id, type_text, press_key, scroll, swipe, open_url, and wait_text. Scroll accepts up or down. Key names are allow-listed. Swipe takes five integers: x1, y1, x2, y2 and duration milliseconds.

Commands:

~~~sh
costphone-engine validate plan.json
costphone-engine run plan.json --dry-run
costphone-engine run plan.json
costphone-engine mcp
~~~

The CLI and MCP share the same preview behavior. In OpenCode, call `phone_run_plan` with both `plan` and `dry_run: true` to validate and preview without calling `amctl`. A dry run does not bypass the sensitive-action policy; a plan containing a blocked sensitive action remains blocked.

~~~text
phone_run_plan({ "plan": { ... }, "dry_run": true })
~~~

A plan can be validated with phone_validate_plan before execution. Use phone_run_plan for the actual staged run.

## Safety and limits

- The engine is not a language model. The calling agent translates the user request into a plan; the engine executes and verifies that plan.
- Package assertions alone are not enough. Add visible text that distinguishes the intended screen, recipient, file, or form.
- For sensitive controls, requires must include destination/context and relevant content. The engine takes a fresh snapshot immediately before the action. Use not_contains in expect when a visible distractor would make the destination ambiguous.
- A failed action is never retried automatically, because an action can succeed even if its confirmation is lost.
- Each execution stage returns an error if the preconditions or postconditions do not match. Earlier actions in a multi-stage plan may already have happened; inspect the returned stage and do not blindly rerun the whole plan.
- Successful live results report `postconditions_matched`, a timing breakdown, and `verification_scope=foreground_package_and_visible_accessibility_text`. Do not infer that a real-world operation (such as sending a message) succeeded merely because the expected text appears somewhere in an accessibility snapshot. Require evidence specific to the destination and result; otherwise report the outcome as unverified.
- Short tasks target roughly 10–20 seconds where the app, device and accessibility service permit it; this is a performance goal, not a guarantee. Use the timing breakdown to identify actual costs; never skip required context checks to meet a target.
- Keep Android's Accessibility and Shizuku authorizations intact. The engine does not bypass permissions or disable the existing guard.
- The 10–20 second target applies to short, low-complexity tasks only. Multi-screen workflows, slow apps, permissions, network waits, and external confirmations can take longer.

## Development

Run the offline suite with:

~~~sh
python3 -m py_compile engine/costphone_engine.py engine/mcp_server.py engine/test_engine.py
python3 -m unittest discover -s engine -p 'test_*.py' -v
~~~

The isolated workflow runs these checks on pushes to the engine branch and on applicable pull requests.
