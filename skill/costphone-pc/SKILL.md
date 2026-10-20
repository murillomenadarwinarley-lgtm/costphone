---
name: costphone-pc
description: Operate the local Windows desktop through CostPhone PC's native MCP tools. Use only when desktop_* tools are available.
---

# CostPhone PC (Windows local)

This MCP controls the Windows desktop on the same PC where OpenCode is running. It is independent of the Android/Termux engine; do not use phone_* tools to control this PC.

## Safe interaction loop

1. Call desktop_inspect to identify the foreground application, then desktop_screenshot to see the current visible UI.
2. Interpret only what is visible in the latest screenshot. If the window or its content is unclear, take another screenshot before acting.
3. For app launches, use desktop_launch_app with one concrete .exe (no arguments). For a web page, use desktop_open_url with a complete HTTP/HTTPS URL.
4. Keyboard and mouse tools may be disabled unless the user explicitly enabled input during installation. If a tool returns input_disabled, stop and explain that permission is disabled; do not work around it.
5. Before desktop_click, use the latest screenshot and choose coordinates that clearly match the intended control. After each click, typing action, or key press, inspect the result again. Never replay an action blindly after a timeout or uncertain result.
6. Confirm the focused input before desktop_type_text. This tool temporarily uses the clipboard to paste Unicode text and restores the previous text clipboard when possible.
7. Before an action that could send/post a message, delete or publish information, submit a form, purchase something, or commit an external change, explain the intended outcome and obtain the user's confirmation. Enter can submit forms and messages; do not press it merely to dismiss uncertainty.
8. Do not infer an external outcome from a click or key press alone. Verify visible confirmation where possible and describe uncertainty honestly.

## Limits

- desktop_inspect reports window/process metadata, not text inside the application.
- desktop_screenshot returns a screenshot image. Do not capture or expose information that is not needed for the task.
- This initial Windows implementation does not have Android's accessibility tree, amctl, display isolation, or automatic OCR.
- The active, unlocked Windows desktop is required; a locked screen or logged-off session is not a supported automation target.
