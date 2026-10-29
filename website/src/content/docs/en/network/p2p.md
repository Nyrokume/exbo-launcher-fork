---
title: P2P later
description: The P2P mode and speed limits are saved. They are not passed to SessionManager.
---

The **ПОВЕДЕНИЕ** column has three «P2P-загрузка» positions: «Всегда», «Кроме мобильных сетей», «Никогда». The model default is «Кроме мобильных сетей» (`P2pMode.EXCEPT_MOBILE`).

The **ОБНОВЛЕНИЕ** block has two «Ограничение скорости» fields: download and upload, KB/s, 0 means unlimited. The tooltip is «В КБ/с, 0 — без ограничений».

Every change writes `p2pMode`, `downloadLimitKb`, and `uploadLimitKb` into `settings.properties` at once (`ExboSettingsStore`). Importing the installed launcher's `settings.json` fills the same fields (`p2p`, `download`, `upload`).

They do not affect the torrent. `syncClientTorrent` builds a `SessionManager`, starts the session, and downloads the catalog. `p2pMode`, `downloadLimitKb`, and `uploadLimitKb` are not passed into that call. Nothing in the repo wires the limits into the session.

On mock, download does not open a `SessionManager` at all: `MockUpdateService` draws the progress.

The string `settings.auto_update` exists in the bundle. `settings.fxml` has no switch with that text. The on-screen «ОБНОВЛЕНИЕ» block is the speed limit only.

## Next

- [Behavior](../../settings/behavior/) — the other switches in that column;
- [Launch](../../game/launch/) — what the download button actually does;
- [Limitations](../../reference/limitations/) — the short list of gaps like this one.
