---
navigation:
  title: "Cross dimensional"
  icon: "anvilcraft_pullwires:cross_dimensional_pole"
# Item binding: hold W (Ageratum's key.ageratum.more_info) on this item to jump here.
guide:
  items:
    - anvilcraft_pullwires:cross_dimensional_pole
---

# Cross dimensional transfer

The Cross Dimensional Pole is the only pole in this addon with **genuinely new behaviour**:
it supplies nothing, never auto-connects, and acts purely as a link endpoint
with **no dimension and no distance limit**.

It extends AnvilCraft's Remote Transmission Pole, so it looks and places exactly the same.

## Three "never"

| Item | Behaviour |
| --- | --- |
| Supply range | 0 — it is only an endpoint |
| Auto-connect | Never joins a grid automatically, at any distance |
| Power line rendering | **Never draws a line**; portal particles at the top show the live link |

## When may a link cross dimensions

::: warning
Only when **both ends are Cross Dimensional Poles**.
If either end is an ordinary pole the attempt is refused — otherwise one cross dimensional pole
would be a free pass for anything.
:::

Linking works exactly like anywhere else:

1. Right-click a Cross Dimensional Pole in the Overworld to bind the master
2. Walk through a portal and right-click (or place) one in the Nether
3. The link is created and portal particles appear at both tops

Two Cross Dimensional Poles in the **same** dimension can of course link as well,
also with **no distance limit**.

## Single chunk loading

The far end of a cross dimensional link lives in another dimension, and that side unloads when nobody
is there — leaving the link "not connected". So a placed Cross Dimensional Pole keeps
**exactly the single chunk it occupies** loaded, reusing AnvilCraft's Overseer loading
(level 0 = one chunk, no random tick).

- Each pole holds **one** chunk; breaking the pole releases it
- Can be turned off with `crossDimensionalPoleForceload`
- Chunk unload and server shutdown do **not** release it — otherwise it would never load again after a restart

## Transfer loss

`crossDimensionalPoleLoss` (0.0-1.0, default 0.0): the slave receives `amount * (1 - loss)`
and the difference simply disappears; it is not returned to the master.
It applies **only when the two ends are in different dimensions** — two cross dimensional poles
in the same dimension are not "teleporting" anything.

## Portal particles

While the link is **active**, both pole tops emit portal particles: they appear at random positions
around the pole head and fly straight into it (the vanilla Nether portal particle motion).
Suspend or break the link and they stop immediately.

## Also

- Priority, locking, deficit breaker, reconnect and switch restart work exactly as for ordinary poles
- `A → B` and `B → A` cannot double the power: settlement is one-way and locally conservative,
  and a slave that was just filled ends at exactly 0, so it can never send anything back in the same tick
