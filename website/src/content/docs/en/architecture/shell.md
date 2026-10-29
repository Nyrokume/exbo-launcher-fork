---
title: Shell scale
description: The sidebar is 253 px at 1024×640 and tracks 220–340 px. Settings have no 820 px cap.
---

The main window is 1024×640, minimum 900×580, resized from the edges. At that width the game column is **253 px**.

`ServersListController.followWindow` recomputes the width from the window: `windowWidth * 253 / 1024`, then clamps to **220–340 px**. The logo scales with the column (`logoWidthFor`, 140 to 220 px from a 170 px logo at 253). The game caption stays inside the row.

<div class="size-table">
<table>
<thead>
<tr><th>Window width</th><th>Column</th></tr>
</thead>
<tbody>
<tr><td>900 (window minimum)</td><td>222 px (<code>900 × 253 / 1024</code>; the 220 floor is not reached yet)</td></tr>
<tr><td>1024</td><td>253 px</td></tr>
<tr><td>1280</td><td>316 px</td></tr>
<tr><td>from about 1376</td><td>340 px, then it stops growing</td></tr>
</tbody>
</table>
</div>

The 220 px floor would apply near a window width of 890 px. The main window's minimum is 900, so the narrowest window sits just above that floor.

The settings column fills the content area. `.settings-content` no longer has an **820 px** cap (`-fx-max-width: infinity`). At a 1280 px window the tunnel page uses the full content width, with no empty band to the right of the cards.

The tunnel heading, the action bar, and the group cards are separate blocks. At 900×580 and 1280×760 the «До вас» / «Пинг» labels fit in full.

Pre-main is not on this scale. Its width stays 460. The update window before show is [460×316](../../getting-started/update/).

## Next

- [Windows and sizes](../windows/) — the two stages;
- [Tunnel layout](../../settings/tunnel/) — the blocks inside the stretched column;
- [Servers](../../game/servers/) — what sits in the 253 px column.
