---
navigation:
  title: "Introduction"
  icon: "anvilcraft_pullwires:ultra_long_range_pole"
  position: -1
---

# AnvilCraft: Pull Wires

This addon adds a new way to connect AnvilCraft's power poles: **bound links**. It lets you pull a **one-way** power channel between **any two poles**, far beyond the automatic grid range — and across dimensions when both ends are Cross Dimensional Poles.

## What it solves

AnvilCraft's poles form a grid automatically, but two poles too far apart simply never join. When you want to send power a long way, or into another dimension, automatic grouping cannot help.

Bound links exist for exactly that:

- One pole **sends** (master), the other **receives** (slave)
- Power flows **one way**; the slave never sends anything back
- One master can feed several slaves
- Priorities, under-voltage cut-off and automatic reconnection are included
- Two **Cross Dimensional Poles** may link across dimensions

## Three minutes to start

1. Hold a pole (or a **disk**) and **right-click** a placed pole — it becomes the **master**, and a distance bar appears.
2. Walk to the target spot and **place** a new pole; the link is created.
3. The pole in your hand is **not consumed**, so you can place several in a row.
4. **Shift + right-click** (even at air) clears the binding.

<tip>
If you do not want to use a pole as the "key", use a **disk** instead: an empty disk right-clicked on a pole remembers the master; right-clicking another pole **pastes** the link on the spot. This is the recommended way for cross-dimension links.
</tip>

## Key concepts

| Concept | Meaning |
| --- | --- |
| **Master / slave** | The two ends of a link. Only the master sends power |
| **Three ranges** | Supply / auto-connect / manual connect — all different, see [Ranges](010_ranges.md) |
| **Binding** | "Which pole is the master" is stored on the item you hold; placing a pole uses it |
| **Settlement** | Runs every few ticks and shares the master's surplus among slaves by priority |
| **Suspended** | A master that stays in deficit stops its own links; slaves then request reconnection periodically |

## Guide map

| Page | Content |
| --- | --- |
| [Ranges](010_ranges.md) | The three distances, how they are measured, per-pole values |
| [Bound links](020_linking.md) | Binding, pasting, off-hand disks, auto-unbind, failure messages |
| [Settlement](030_settlement.md) | One-way transfer, priority, config page, locking, cut-off, reconnect |
| [Cross dimension](040_cross_dimension.md) | Cross Dimensional Pole, chunk loading, loss |
| [Commands](050_commands.md) | `/wires` commands, connection list, states, config files |

## Opening this guide

- Run `/wires guide` at any time
- Holding either of the addon's poles, press **W** to jump to its page

<info>
This guide is plain Markdown rendered in game by Ageratum. Pages can be bookmarked, zoomed with the scroll wheel, and dragged around.
</info>
