---
navigation:
  title: "Binding links"
  icon: "anvilcraft_pullwires:cross_dimensional_pole"
---

# Binding links

A binding link is how you explicitly say "these two poles are connected".
It does not interfere with auto-connecting: auto-connecting merges poles into **one grid**,
while a binding link is a **one-way power channel between two grids**.

## Bind and unbind

| Action | Result |
| --- | --- |
| **Right-click** a placed pole | It becomes the **master**; the binding is written onto the item you hold |
| **Shift + right-click** (at the air too) | Clear the binding |

The binding is **not consumed**: after placing one pole you still hold it and can place the next one.

## Four ways to create a link

### 1. Place a pole (pole item in hand)

Right-click the master, walk to the target spot, place a pole.

### 2. Paste (bound disk in hand)

When the disk already holds a master, **right-click another placed pole** to link on the spot;
the clicked pole becomes the **slave**. This does not overwrite the stored master, so you can paste
onto many poles in a row.

::: tip
Right-clicking a pole with an **empty** disk stores the master into it (AnvilCraft's "copy").
To turn it back into an ordinary disk: shift + right-click to clear the binding.
:::

### 3. Offhand disk + placing with the main hand

Put the bound disk in your **offhand**, hold a pole in your main hand and place it.
The guide band and the distance bar show up as usual. Controlled by `allowDiskBinding`.

### 4. Debug command

`/wires link <A> <B>` creates A → B directly. OP only, and meant for troubleshooting.

## Feedback

- **Particle guide band** from the master's top to the head of the placement preview;
  it pauses once the distance is out of range.
- **Distance boss bar**: green over the first 3/4 (in range), red over the last 1/4 (out of range).
  When the master is a Cross Dimensional Pole it is a full green bar reading "no distance limit".
- **Item tooltip** on the held pole or disk: the three ranges plus binding state
  (master position, priority, locked).

## Failure messages

| Situation | Message | Binding |
| --- | --- | --- |
| Beyond the master's manual range | out of manual connect range (current / limit) | **kept**, just move closer |
| Cross dimension without two cross dimensional poles | cross dimensional links need two Cross Dimensional Poles | **kept** |
| A link in this direction already exists | a link in this direction already exists | **kept** |
| Pasting onto the master itself | this pole is the stored master | **kept** |

The failure sound is the same note block sound used when a link breaks.

::: info
`A → B` and `B → A` are two **independent** links and may coexist.
:::
