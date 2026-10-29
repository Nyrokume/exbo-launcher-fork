---
title: JVM
description: Preset cards and the active-profile line.
---

**JVM** opens its own page. A summary card sits above the list. With nothing chosen the kicker is “Now”, the name is “none”, and the hint is “Pick a config below. It is what the game will launch with.” After a click the kicker is “Selected for launch”, the name and heap (`v1.1.2, 6 GB`), and the preset hint. The chosen summary uses a `-primary` border.

The cards sit in two columns. A card shows the version with the heap and the preset hint. The chosen card uses a `-primary` border and a ring filled with `-primary`. A click calls `StalzoneDesk.activatePreset`: it writes `configs/<id>.json` and makes that id active. The message is “Profile … is saved. Restart the game.” The id `8khz` is labeled “8 kHz profile”. Any other id is kept as-is.

`service.exe` reads the active profile on the next game start. The launcher does not install IFEO.

## Presets

The page order is `JvmPresets.all`, newest first, then the Aikar example. The generators come from EXBO-Community/stalcraft-jvm-optimization. Large pages and the big-cache / slow-RAM branches stay off. Free memory is not sampled: the generator receives both total and free as the whole machine.

| Version | Id | Hint, when the generator builds a profile |
|---|---|---|
| v1.1.2 | `v1.1.2/default` | Calm G1 pauses, heap up to 6 GB |
| v1.1.1 | `v1.1.1/default` | The same calculation as v1.1.2 |
| v1.1.0 | `v1.1.0/default` | More aggressive, heap up to 8 GB |
| v1.0.8 | `v1.0.8/default` | At least 4 GB and a calm JIT |
| v1.0.7 | `v1.0.7/default` | A strong-CPU branch, at least 6 GB |
| v1.0.6 | `v1.0.6/default` | An 8 GB ceiling, 6 GB free required |
| v1.0.5 | `v1.0.5/default` | The same calculation as v1.0.4 |
| v1.0.4 | `v1.0.4/default` | Early flags, at least 6 GB |
| aikar | `aikar` | The Aikar example for OpenJDK 9 |

If the generator would allocate no heap (too little RAM), the card is disabled and reads “Not enough memory for this profile”.

The launcher-page RAM bar runs from 2 GB to the whole gigabytes of the machine, in 1 GB steps. The last 2 GB show “Little memory will be left for the system”. Once the bar has a chosen value, a preset click writes that heap instead of the generator’s number and retunes the fields derived from the heap (region size; on v1.0.x also metaspace, soft-ref, and the code cache). GC and JIT flags stay. Until the bar is chosen, the heap comes from the generator.

On live the file is `%APPDATA%\EXBO\jvm_wrapper\configs`, and the active name is `HKCU\Software\StalcraftWrapper\ActiveConfig`. On mock that is a temp directory and `MemoryRegistry`.

## What next

- [Tunnel](../tunnel/) — the other file under `jvm_wrapper`;
- [Launch flags](../../reference/gradle-flags/) — properties of the launcher process, not of the game.
