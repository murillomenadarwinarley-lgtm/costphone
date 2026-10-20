# CostPhone UI refresh

This branch introduces a native Android UI refresh for the CostPhone launcher.

## Navigation

- **Inicio** — service status and shortcuts to the other sections.
- **Control** — copyable examples for `amctl launch`, `tap`, `swipe`, and `type`.
- **Inspector** — copyable examples for `amctl snap`, `tree`, `see`, and `tapref`.
- **Rutinas** — example command sequences that can be copied and pasted in Termux.
- **Ajustes** — current AccessibilityService state, plus Termux and virtual-display notes.

## Icon assets

The app uses Android vector drawables under `app/res/drawable/` for navigation and action icons. They are tintable and scale without the blur associated with small raster icons.

## Honest execution state

The launcher reads Android's actual enabled AccessibilityService list. The Control, Inspector, and Rutinas pages currently provide commands to copy to Termux; they do not claim to send or execute remote commands directly from those pages.

## Build

The existing project build script still produces a signed debug APK:

```sh
cd app
bash build.sh
```

The workflow `.github/workflows/build-costphone.yml` compiles the APK in GitHub Actions and uploads it as a short-lived artifact if the build succeeds.
