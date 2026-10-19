---
navigation:
  title: "Cross dimension"
  icon: "anvilcraft_pullwires:cross_dimensional_pole"
# Item binding: press W while holding this item to open this page.
guide:
  items:
    - anvilcraft_pullwires:cross_dimensional_pole
---

# Cross dimension

The Cross Dimensional Pole is the addon's second pole. It **supplies nothing and never groups automatically**; it is only a link endpoint, and it is **not limited by distance or dimension**.

Its shape reuses AnvilCraft's four-part Remote Transmission Pole, so placing it works exactly like any other pole.

## What it is

| Property | Behaviour |
| --- | --- |
| Supply range | 0: it is only an endpoint (unless it physically touches a grid) |
| Auto-connect | 0: it never joins a grid automatically at any distance |
| Manual connect | unlimited |
| Cross dimension | allowed |

So it neither generates nor seeks power by itself — it is one end of a wire.

## How to cross dimensions

<warning>
A cross-dimension link is only allowed when **both ends are Cross Dimensional Poles**. A single Cross Dimensional Pole paired with an ordinary pole is rejected — otherwise one pole would hand out free cross-dimension transport.
</warning>

Steps:

1. Right-click a Cross Dimensional Pole in the Overworld to store the master (a disk is recommended)
2. Step through the portal and right-click another Cross Dimensional Pole there, or place a new one
3. The link is created and both pole tops start emitting portal particles

Two Cross Dimensional Poles in the same dimension obviously link as well, again with no distance limit.

## Power lines

Like automatically grouped poles, a **same-dimension** link draws a power line. Only a link that **actually crosses dimensions** draws no line; it is shown by the portal particles at the pole tops instead.

## Single-chunk loading

The far end of a cross-dimension link lives in another dimension and would unload when nobody is there, leaving the link "Inactive". So a placed Cross Dimensional Pole force-loads **its own single chunk** (AnvilCraft's loading machinery, level 0 = exactly one chunk, no random ticks).

- One chunk per pole, released when the pole is broken
- Can be disabled with `crossDimensionalPoleForceload`
- Chunk unload or server shutdown does **not** release it, otherwise it would never load itself again after a restart

## Loss

`crossDimensionalPoleLoss` (0.0 – 1.0, default 0.0) applies only to links that **really cross dimensions**.

The loss is paid by the **master**, not deducted from the delivery:

```text
To deliver N, the master pays N / (1 − loss)
```

With loss 0.2 and a slave needing 256, the master pays 320 and the slave receives exactly 256.

If the master cannot afford that, it sends what it has and the slave receives less — the slave goes under-powered instead of the master's balance going negative.

<info>
With loss 0 the master pays exactly what the slave receives. A same-dimension link has no loss at all.
</info>

## Portal particles

While a link is **Active**, both pole tops emit portal particles: they appear around the head and travel into it. They stop immediately when the link is suspended or disconnected.

## Appearance

It has a single head appearance and **no switch or overload texture variants** — it takes no part in power supply, so there is no "overload" state.

The core of the head is rendered with the **End Portal** effect: it shows a deep starfield that does not slide with the surface, unlike the pole's regular textures.

## Notes

- Priority, locking, cut-off, reconnection and switch restart behave exactly like ordinary poles
- `A → B` and `B → A` together do not double the power: settlement is one-way and locally conservative
