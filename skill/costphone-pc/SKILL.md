---
name: costphone-pc
description: Operate the local Windows desktop through CostPhone PC's native MCP tools. Use only when desktop_* tools are available.
---

# CostPhone PC (Windows local)

This MCP controls the Windows desktop on the same PC where OpenCode is running. It is independent of the Android/Termux engine; do not use phone_* tools to control this PC.

## Safe interaction loop

1. Call desktop_inspect to identify the foreground application, then call desktop_inspect_controls to read its visible Windows UI Automation controls. Use desktop_screenshot when labels are unclear, a control is not exposed, or you need visual context.
2. Prefer desktop_inspect_controls selectors (name, automation_id, control_type, occurrence) to coordinate clicking. Every selector is bound to a window_handle and process_id. The motor rejects a selector if the foreground window changes before acting. If a control is not found or the app changes, inspect the window and controls again; do not reconstruct selectors without these scope values.
3. Use desktop_click_control for an exact UI Automation target. If the application exposes no usable controls, fall back to desktop_screenshot followed by desktop_click at coordinates from that recent screenshot.
4. Use desktop_set_control_text only for controls Windows identifies as Edit fields. It requires explicit input permission and refuses elements identified as password fields. Confirm that the target field is correct before writing.
5. For generic typing, desktop_type_text pastes Unicode text to the focused control. Verify the focused field before using it. The previous clipboard text is restored when possible.
6. For app launches, use desktop_launch_app with one concrete .exe (no arguments). For a web page, use desktop_open_url with a complete HTTP/HTTPS URL.
7. Keyboard/mouse and UI control changes may be disabled unless the user explicitly enabled input during installation. If a tool returns input_disabled, stop and explain that permission is disabled; do not work around it.
8. After every click, typing action, or key press, inspect the state again. Never replay an action blindly after a timeout or uncertain result.
9. Before an action that could send/post a message, delete or publish information, submit a form, purchase something, or commit an external change, explain the intended outcome and obtain the user's confirmation. Enter can submit forms and messages; do not press it merely to dismiss uncertainty.
10. Do not infer an external outcome from a click or key press alone. Verify visible confirmation where possible and describe uncertainty honestly.

## Limits

- desktop_inspect reports window/process metadata. desktop_inspect_controls reads visible controls exposed by Windows UI Automation, but some apps (games, remote desktops, custom-rendered surfaces, elevated windows, etc.) expose few or no useful controls.
- desktop_screenshot returns an image for visual interpretation. Do not capture or expose information that is not needed for the task.
- The native Windows engine does not have Android amctl, Android-only permissions, or the isolated virtual display implementation.
- The active, unlocked Windows desktop is required; a locked screen or logged-off session is not a supported automation target.
