---
navigation:
  title: "Bound links"
  icon: "anvilcraft_pullwires:cross_dimensional_pole"
---

# Bound links

Automatic grouping answers "put these poles into one grid". Bound links answer "**draw a channel between two grids**". They do not interfere: automatic grouping keeps working, and a bound link adds one one-way channel between the two grids.

## Bind and unbind

| Action | Result |
| --- | --- |
| **Right-click** a placed pole | It becomes the **master**; the binding is stored on the item you hold |
| **Shift + right-click** (even at air) | Clear the binding |

The binding is **not consumed**: after placing one pole and creating a link, the binding stays, so you can place a second, third, and so on.

<info>
Picked the wrong pole? No need to unbind: right-click another pole and the master is simply replaced.
</info>

## Three ways to create a link

### 1. Place a pole

Hold a pole, right-click the master, walk to the target spot and place it.

### 2. Paste with a disk

When a disk already remembers a master, **right-click another placed pole** to create the link on the spot; the clicked pole becomes the **slave**.

This does not clear the master stored in the disk, so you can paste onto many poles in a row.

An empty disk right-clicked on a pole stores that master.

<tip>
For cross-dimension work use a disk: you cannot carry a placeable pole through a portal and "place" it there, but you can carry a disk and right-click a Cross Dimensional Pole that is already there.
</tip>

### 3. Off-hand disk + main-hand placement

Put the bound disk in your **off hand** and place poles normally with the main hand; the link is created as usual. Controlled by `allowDiskBinding` (on by default).

## Guide beam and distance bar

While bound you see:

- **Particle guide beam** from the master's top to the top of the placement preview; it stops being emitted once out of range
- **Distance bar** at the top of the screen: the first 3/4 is green (in range), the last 1/4 is red (out of range)

When the master is a Cross Dimensional Pole the bar is fully green and reads "no distance limit".

## Automatic unbind on distance

Carrying a binding too far unbinds it automatically, so you do not walk around with a link that can never connect.

| Item | Value |
| --- | --- |
| Threshold | manual connect range × `cancelDistanceMultiplier` (2× by default) |
| Feedback | distance bar disappears, action bar says it was cleared, note-block sound |
| Exception | never triggered when the master is a Cross Dimensional Pole |

## Failure messages

| Situation | Action bar | Binding |
| --- | --- | --- |
| Beyond the manual connect range | Out of manual range (current / limit) | Kept |
| Cross-dimension with a non-cross-dimension end | Cross-dimension links only work between two Cross Dimensional Poles | Kept |
| A same-direction link already exists | Same-direction link already exists | Kept |
| Pasting onto the master itself | This pole is the current master | Kept |

The binding is always kept on failure — just move and try again.

<warning>
`A → B` and `B → A` are **two independent links** and may coexist (mutual master/slave). Multi-hop relaying does not need this; see [Settlement](030_settlement.md).
</warning>
