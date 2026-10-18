---
navigation:
  title: "Settlement"
  icon: "anvilcraft_pullwires:ultra_long_range_pole"
---

# Settlement and priority

## One-way transfer

Every link is a **directed edge**: only the **master** sends to the **slave**,
and the slave never sends anything back. "Who feeds whom" is decided the moment you bind.

Settlement runs once every `powerGridTick`: the master must have a surplus **and** the slave a deficit.
Both are required.

::: info
With one master and several slaves, the master's surplus is handed out by
**priority descending, then creation time ascending**.
:::

## Priority (0-15)

| Item | Value |
| --- | --- |
| Default | 7 |
| Change | Hold the **Anvil Hammer**, hold the hotkey (default Left Ctrl) and **right-click** the pole, then pick a value on the wheel in its settings page: point at a section or scroll to move the selection, **left-click the wheel to confirm** |
| Meaning | Higher numbers are served first |

Besides priority, the settings page also holds that pole's **reconnect sorting** (below).
No action bar spam: the pole's live value is shown in the Anvil Hammer's tooltip instead.

## Reconnect sorting

After the deficit breaker suspends a link, the slave asks for a reconnect periodically;
when one master gets several requests in the same tick it judges them in sorted order.
Sorting is **per pole** and is edited on that pole's settings page:

| Button | Meaning |
| --- | --- |
| Primary factor | "priority first" or "demand first" (demand = the slave's average deficit) |
| Primary direction | ascending / descending |
| Secondary direction | the secondary factor is always the one that is not the primary factor; this only sets its direction |

**The time factor is always ascending and has no button**: when both factors tie, the earlier link is considered first.
Sorting applies while **this pole acts as the master** judging its own requests.

There is **no "sorting source" switch**: anything you press is written straight into that pole's own ordering.
The "Reconnect Sort" entry in the server config's `Settlement` group is the **save-wide default**;
it is copied once into every pole that has **no ordering of its own yet**, on the first settlement tick after the
world loads. Poles that **already have one are skipped**, so a default change never overwrites an ordering a player
tuned, and running it again changes nothing (it is idempotent).

## Locking

**Right-click** a pole with the Anvil Hammer to toggle locking.

Locking means **ignore redstone-driven switch changes**: once locked, redstone cannot flip
its switch, so it is neither cut off nor restarted through the "switch restart" path.

Locking does **not** affect priority.

## Deficit breaker

When the master's grid stays in deficit for more than `manualLinkDeficitTicks` (default 300 ticks),
the master suspends its links by **average net outflow, largest first**, re-checking after each one.

A suspended link keeps its **definition** — it just stops sending.
It then waits for a reconnect: once the slave's grid has been stable for a while it asks periodically,
and the master agrees if it is not in deficit. `manualLinkDeficitTicks = 0` means never auto-suspend.

## Switch restart

Flipping a pole's switch **OFF → ON** resets all of its runtime values
(deficit timer, net outflow window, reconnect cooldown) while **keeping the link definitions**,
so suspended links come straight back.

## Reading the tooltip

```text
Grid information:
  Total consumption: 80 kW
  Total generation: 160 kW
  Total exchanged: 40 kW     <- inserted by this addon, same level as "Total generation"
Pull Wires (master):
  Sending: 16 kW             <- per tick power, hidden when 0
  Receiving: 8 kW
  Priority: 7
Locked                       <- only while locked
```

The title says whether the pole acts as master, slave, or both.
