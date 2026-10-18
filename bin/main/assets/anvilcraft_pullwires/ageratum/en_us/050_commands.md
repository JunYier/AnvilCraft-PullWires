---
navigation:
  title: "Commands & UI"
  icon: "minecraft:compass"
---

# Commands and UI

## `/wires`

| Subcommand | Permission | Purpose |
| --- | --- | --- |
| `guide` | player | Open this guide |
| `list` | anyone | List every link and its state |
| `link <A> <B>` | OP | Create A → B directly |
| `unlink <A> <B>` | OP | Remove the link between those two poles |
| `clear` | OP | Warn that this is destructive |
| `clear confirm` | OP | Actually clear every link |

Coordinates may point at **any segment** of a pole; the command finds the top itself.
`link` / `unlink` are for troubleshooting — normally you use binding links.

## `/wires display`

A client-side command that toggles the on-screen link list:

```text
/wires display          # toggle
/wires display true     # always on
/wires display false    # hidden
```

The list updates live and shows both endpoints plus a state colour.
While enabled it also draws particle beams along same-dimension links.

## State colours

| State | Meaning |
| --- | --- |
| **Active** | Both ends online, transferring |
| **Not connected** | One end is unloaded or outside a grid; recovers on its own |
| **Suspended** | Cut by the deficit breaker, waiting to reconnect |
| **Awaiting approval** | The slave asked to reconnect; the master decides |

Line colours and state icons are configurable under the client `Graphics & Rendering` group.

## Where to configure

| File | Contents |
| --- | --- |
| `anvilcraft_pullwires-common.toml` | Ranges, cross dimensional settings, settlement, linking switches |
| `anvilcraft_pullwires-client.toml` | Line colours, placement hint, list position and scale, particles |

Every config entry has a description; hover it in the config screen.
