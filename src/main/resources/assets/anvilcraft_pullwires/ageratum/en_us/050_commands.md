---
navigation:
  title: "Commands"
  icon: "minecraft:compass"
---

# Commands

## `/wires`

| Subcommand | Permission | Effect |
| --- | --- | --- |
| `guide` | player | Opens this guide |
| `list` | player | Lists every link and its state |
| `link A B` | OP | Creates A → B directly |
| `unlink A B` | OP | Removes links between the two poles |
| `clear` | OP | Warns that this is destructive |
| `clear confirm` | OP | Actually clears every link |

Coordinates may point at **any part** of a pole; the command finds the top segment itself.

`link` / `unlink` are troubleshooting tools — normal play should use bound links.

## `/wires display`

A client-side command that toggles the on-screen connection list:

| Usage | Effect |
| --- | --- |
| `/wires display` | Toggle |
| `/wires display true` | Always shown |
| `/wires display false` | Hidden |

Each row shows one link: a state marker, the master's dimension and coordinates, the slave's dimension and coordinates, and this tick's transfer. A cross-dimension link shows "sent → delivered (after loss)".

Position, maximum row count and scale are all configurable in the client config.

## States

| State | Meaning |
| --- | --- |
| **Active** | Both ends online, transferring normally |
| **Inactive** | One end is offline or not in a grid; reconnects automatically |
| **Suspended** | Cut off by the under-voltage breaker, waiting for a reconnect request |
| **Pending** | The slave has requested reconnection; awaiting the master's decision |

The colours of all four states are configurable in the client config group `Graphics & Rendering`.

## Config files

| File | Content |
| --- | --- |
| `anvilcraft_pullwires-common.toml` | Three ranges, cross dimension, settlement and reconnection, link toggles |
| `anvilcraft_pullwires-client.toml` | Line colours, placement hint, connection list, particles and UI |

Every entry in the config screen has a description; hover it to read.

## Integrations

- **Jade**: shows this addon's link info when looking at a pole
- **JEI**: both poles have an info page describing rules that do not fit in the recipe
- **Ageratum**: this very guide — reopen it any time with `/wires guide`
