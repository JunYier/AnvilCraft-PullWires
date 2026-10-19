---
navigation:
  title: "Settlement"
  icon: "anvilcraft_pullwires:ultra_long_range_pole"
---

# Settlement

## One-way transfer

Every link is a **directed edge**: only the **master** sends power to the **slave**, and the slave's surplus never flows back.

Settlement runs once every `powerGridTick`: a transfer happens only when the master has a surplus **and** the slave has a deficit.

With one master and several slaves, the master's surplus is shared by **priority descending, then creation time ascending**.

<info>
Multi-hop works: a slave that is also the master of another link uses the power it receives to cover its own downstream. So `A → B → C` does reach C.
</info>

## Priority

| Item | Value |
| --- | --- |
| Range | 0 – 15 |
| Default | 7 |
| Meaning | Higher number gets power **first** |
| Editing | Hold an **anvil hammer**, hold the hotkey (Left Ctrl by default) and **right-click** the pole; drag the slider on the config page |

The slider **submits once on release**; nothing is sent while dragging. After release the value appears once the server syncs.

## Per-pole config page

With an anvil hammer, holding the hotkey and right-clicking a pole opens that pole's own config page:

| Control | Purpose |
| --- | --- |
| Priority slider | 0 – 15, commits on release |
| Primary factor | Switches between "priority" and "demand" |
| Primary / secondary direction | Ascending or descending for each |

The hotkey can be changed in the controls screen (Left Ctrl by default).

<warning>
Only one page opens at a time, and only when the anvil hammer is in the **main hand** and no other screen is open. If pages seem to stack or nothing opens, make sure shift is not held.
</warning>

## Reconnect order

After a link is suspended by the under-voltage cut-off, slaves periodically request reconnection. When several requests reach one master, they are decided in order.

The order is stored **per pole** and edited on the config page:

- **Primary factor**: priority first, or demand first (demand = the slave's average deficit)
- **Time is always ascending and has no switch**: when both factors tie, the older link is considered first

The server config value is a **save-wide default**: the first settlement after joining a world copies it to poles that **have no setting yet**.

- Poles that already have a setting are skipped; a player's choice is never overwritten
- The backfill is idempotent

## Locking

Hold an **anvil hammer** and **shift + right-click** a pole to toggle locking.

**Locking means "ignore redstone-driven switch changes"**: while locked, redstone does not flip the pole's switch, so it is neither cut off nor does it trigger a switch-restart reconnect afterwards.

- Locking does **not** affect priority
- The tooltip only shows a "Locked" line while locked
- Unlocking triggers one neighbour update so the pole does not stay on a stale switch state

## Under-voltage cut-off

When the master's grid stays in deficit beyond `manualLinkDeficitTicks` (300 ticks, about 15 seconds), the master suspends its own links, starting from the largest average net outflow and re-evaluating after each one.

- Suspended links keep their **definition**; only power stops
- `manualLinkDeficitTicks = 0` disables the cut-off entirely
- Cut-off only ever happens on the master side — a slave without power does not drop the link

<info>
With cross-dimension loss, the loss is paid by the **master**: delivering `N` costs `N / (1 − loss)`. If a master looks well supplied yet keeps going into deficit, check the loss coefficient first.
</info>

## Reconnecting after suspension

| Item | Value |
| --- | --- |
| Request interval | the slave asks every `reconnectRequestInterval` ticks (200 ticks, about 10 s) |
| Decision | whether the master's **rolling average surplus** covers the slave's average deficit; no decision before the window is warm |
| Approved | the quota is deducted from the surplus and the link returns to Active |
| Denied | the slave waits for the next cycle; one denial does not starve later requests |
| Requirement | both poles must be online |

## Switch restart

Turning a pole's switch **off and on again** (OFF → ON):

- resets that pole's runtime values (deficit counter, averaging windows, cooldowns)
- **immediately restores** its suspended links

This is how hand-made wiring survives a restart.

## Reading the tooltip

Looking at a pole shows:

| Line | Meaning |
| --- | --- |
| Total consumption / generation | AnvilCraft's own grid statistics |
| Total exchanged | Inserted by this addon right after "Total generation"; only present when there is exchange |
| Pull Wires: | This addon's block |
| Upstream / downstream counts | e.g. `2>>slave` and `master>>3` — how many links point at this pole and how many it feeds |
| Sent / received | This tick's amounts; hidden when zero |
| Priority | This pole's priority |
| Locked | Only while locked |

Numbers use AnvilCraft's power units; hold **Shift** for a more precise reading.
