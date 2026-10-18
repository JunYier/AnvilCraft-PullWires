---
navigation:
  title: "Ranges"
  icon: "anvilcraft_pullwires:ultra_long_range_pole"
# Item binding: hold W on the Ultra Long Range Pole to jump here (Ageratum shows the hint itself).
guide:
  items:
    - anvilcraft_pullwires:ultra_long_range_pole
---

# Ranges

Every pole has three distances, and they mean completely different things:

| Range | What it decides |
| --- | --- |
| **Supply range R_supply** | Whether generators, consumers and storage can join this pole's grid |
| **Auto-connect range R_auto** | Whether this pole automatically joins the same grid as other poles |
| **Manual range R_manual** | How far a binding link may reach (the one this addon adds) |

They must satisfy `R_supply <= R_auto <= R_manual`.

## Per pole type

Ranges are **per pole type**, matching AnvilCraft's own approach:

| Pole | Supply | Auto-connect | Manual |
| --- | --- | --- | --- |
| AnvilCraft Transmission Pole | its own config (default 8) | 1.5x → 12 | 2x → 16 |
| AnvilCraft Remote Transmission Pole | its own config (default 16) | 1.5x → 24 | 2x → 32 |
| Ultra Long Range Pole | 2 | 32x → 256 | 64x → 512 |
| Cross Dimensional Pole | 0 (supplies nothing) | 0 (never auto-connects) | no distance limit |

The multiplier base is **AnvilCraft's R**: AnvilCraft's own two poles use their own R,
while this addon's two poles use the Transmission Pole's R.
The last column shows real block counts with R at its default (8 / 16).

## Why multipliers instead of block counts

AnvilCraft's R is **player editable**. If this addon stored fixed block counts, raising R would make
the supply range exceed the auto-connect range, and **already placed poles in old saves would error out**.

With multipliers the nesting is structural: no matter how R changes, the relation holds.
The two multipliers are also independently adjustable; if one ends up below the other,
it is clamped on read instead of crashing.

::: warning
Raising AnvilCraft's R scales **everything**, including the Ultra Long Range Pole's reach.
:::

## Where to configure

Under `config/anvilcraft_pullwires-common.toml`, group `Transmission`, one collapsible block per pole type.
Changes apply immediately, no restart needed.
