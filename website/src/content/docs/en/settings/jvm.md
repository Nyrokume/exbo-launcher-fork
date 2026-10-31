---
title: JVM
description: Preset cards and the active-profile line.
---

**JVM** opens its own page. A summary card sits above the list. With nothing chosen the kicker is “Now”, the name is “none”, and the hint is “Pick a config below. It is what the game will launch with.” After a click the kicker is “Selected for launch”, the name and heap (`v1.1.2, 6 GB`), and the preset hint. The chosen summary uses a `-primary` border.

The cards sit in two columns. A card shows the version with the heap and the preset hint. The chosen card uses a `-primary` border and a ring filled with `-primary`. A click calls `StalzoneDesk.activatePreset`: it writes `configs/<id>.json` and makes that id active. The message is “Profile … is saved. Restart the game.” The id `8khz` is labeled “8 kHz profile”. Any other id is kept as-is.

`service.exe` reads the active profile on the next game start. The launcher does not install IFEO.

## Presets

The page order is `JvmPresets.choices`. The first card is **No JVM**: `StalzoneDesk.useTunedProfile(false)` clears the active name and does not write a preset file. The summary becomes “No JVM”, the hint is “The game starts with its own flags. The wrapper does not inject a profile.”, and the message is “JVM profile cleared. Restart the game.”

The cards after that write a file. The generators come from EXBO-Community/stalcraft-jvm-optimization. On an installed launcher the profile is built from this PC: threads, physical cores, free memory, slow memory (up to 2933 MT/s), a large L3 (64 MB or more), and large pages when that privilege is already enabled. Unknown memory speed is not treated as slow. The 8 kHz profile and Aikar stay as written.

One line under the summary names the CPU and GPU, the core count, the thread count, and the RAM. Slow memory, a large cache, and large pages are appended on that line when the read found them. The showcase hides the line: `machine()` is empty there, so screenshots do not drift.

| Label | Id | Hint |
|---|---|---|
| No JVM | a blank active id | The game starts with its own flags. The wrapper does not inject a profile. |
| 8 kHz profile | `8khz` | Short G1 pauses, 16 MB regions |
| v1.1.2 | `v1.1.2/default` | Calmer G1 pauses |
| v1.1.0 | `v1.1.0/default` | Shorter pauses and string deduplication |
| v1.0.8 | `v1.0.8/default` | Calmer JIT on a weak CPU |
| v1.0.7 | `v1.0.7/default` | Skips huge methods on a weak CPU |
| v1.0.4 | `v1.0.4/default` | Early G1 flags |
| aikar | `aikar` | Aikar flags, pauses up to 150 ms |

`build` still accepts the hidden copies. A stored `v1.1.1/default` lights the v1.1.2 card. `v1.0.5/default` and `v1.0.6/default` light v1.0.4. Those ids have no card of their own.

If the generator would allocate no heap (too little RAM), the card is disabled and reads “Not enough memory for this profile”. The “No JVM” card stays enabled.

The launcher-page RAM bar runs from 2 GB up to the machine's memory, and never above 8 GB, in 1 GB steps. “Little memory will be left for the system” appears only when the machine would keep less than 2 GB. Once the bar has a chosen value, a preset click writes that heap instead of the generator’s number and retunes the fields derived from the heap (region size; on v1.0.x also metaspace, soft-ref, and the code cache). GC and JIT flags stay. Until the bar is chosen, the heap comes from the generator.

On live the file is `%APPDATA%\EXBO\jvm_wrapper\configs`, and the active name is `HKCU\Software\StalcraftWrapper\ActiveConfig`. On mock that is a temp directory and `MemoryRegistry`.

## What next

- [Localization](../localization/) — item names in the client language file;
- [Tunnel](../tunnel/) — the other file under `jvm_wrapper`;
- [Launch flags](../../reference/gradle-flags/) — properties of the launcher process, not of the game.
