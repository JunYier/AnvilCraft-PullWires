---
navigation:
  title: "Ranges"
  icon: "anvilcraft_pullwires:ultra_long_range_pole"
# Item binding: press W while holding the Ultra Long Range Pole to open this page.
guide:
  items:
    - anvilcraft_pullwires:ultra_long_range_pole
---

# Ranges

Every pole has three different distances, and they mean completely different things.

| Range | Decides |
| --- | --- |
| **Supply range** | Whether generators, consumers and storage join the grid this pole belongs to |
| **Auto-connect range** | Whether two poles join the **same grid automatically** |
| **Manual connect range** | How far a bound link may reach |

They must satisfy `supply ≤ auto-connect ≤ manual connect`, otherwise you get the odd case of "connected but not powered".

## How distance is measured

All three ranges use the **long side**: `max(|dx|, |dy|, |dz|)`.

Two poles at `(0, 0, 0)` and `(12, 12, 0)` are therefore **12** apart, not 16.97.

<info>
This keeps diagonal placement from being punished. Vanilla grid grouping uses box intersection, which gets stricter on diagonals; this addon uses one plain rule instead.
</info>

## Values per pole

| Pole | Supply | Auto-connect | Manual connect |
| --- | --- | --- | --- |
| AnvilCraft Transmission Pole | 8 (its own R) | 24 (3×) | 32 (4×) |
| AnvilCraft Remote Transmission Pole | 16 (its own R) | 48 (3×) | 64 (4×) |
| Ultra Long Range Pole | 2 | 128 (16× the Transmission Pole R) | 256 (32×) |
| Cross Dimensional Pole | 0 (no supply) | 0 (never groups) | unlimited |

The AnvilCraft pole numbers assume the **default R** (8 and 16). Changing the vanilla R changes both the auto-connect and the manual connect ranges.

## Why multiples instead of block counts

The vanilla R is **configurable**. If this addon hardcoded block counts, raising R would make the supply range larger than the auto-connect range — a corrupt configuration that even breaks poles already placed in old saves.

With multiples the nesting is structural: no matter how R changes, the relation holds.

The Ultra Long Range Pole also uses multiples for auto-connect and manual connect (16× / 32×), but its **supply range is fixed at 2** — it is nearly purely a long-distance relay.

## Placement hint

Holding one of the addon's poles, if the aimed spot would fall inside an existing pole's **auto-connect range**, a green `><` hint appears, meaning "placing here joins that grid".

## Where to configure

`config/anvilcraft_pullwires-common.toml`, group `Transmission`, one collapsible block per pole type.

<warning>
Code defaults are only written when the config file is **created**. If you already have a config file from an older version, edit it manually or delete it so it is regenerated — otherwise the old values stay in effect.
</warning>
