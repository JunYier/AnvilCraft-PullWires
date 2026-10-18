---
navigation:
  title: "Introduction"
  icon: "anvilcraft_pullwires:ultra_long_range_pole"
  position: -1
---

# AnvilCraft: Pull Wires

> This addon adds a new way to connect AnvilCraft's transmission poles: **binding links**.
> They pull a **one-way** power channel between any two poles — far beyond auto-connect range,
> and even across dimensions.

## The problem it solves

AnvilCraft's poles form power grids by auto-connecting, which is inherently short ranged.
When you want to send power very far away, or into another dimension, auto-connect cannot help.
Binding links are made for exactly that:

- One pole **sends** (master), the other **receives** (slave)
- Power flows **one way**; the slave never sends anything back
- One master can feed many slaves
- Two **Cross Dimensional Poles** can link across dimensions

## Get started in three minutes

1. Hold a pole (or a disk) and **right-click** a placed pole → it becomes the **master**,
   and a distance boss bar appears.
2. Walk to the target spot and **place** a new pole → the link is created, and the action bar says so.
3. The binding is **not consumed**: as many poles as you carry, that many links you can make.
4. **Shift + right-click** (even at the air) clears the binding.

::: tip
You do not have to use a pole as the "key". With a **disk**: right-click a pole with an empty disk
to store the master, then right-click another pole to **paste** and link on the spot.
That is the usual way to do it across dimensions.
:::

## Guide contents

| Page | What is in it |
| --- | --- |
| [Ranges](010_ranges.md) | Supply / auto-connect / manual ranges, and why they are multipliers |
| [Binding links](020_linking.md) | Bind, paste, offhand disk, failure messages |
| [Settlement](030_settlement.md) | One-way transfer, priority, locking, deficit breaker |
| [Cross dimensional](040_cross_dimension.md) | Cross dimensional poles, single chunk loading, loss |
| [Commands & UI](050_commands.md) | `/wires` commands and the on-screen link list |

## About this guide

It is plain Markdown, rendered in game by [Ageratum](https://www.anvilcraft.dev/posts/docs/ageratum/).
Reopen it any time with `/wires guide`.
