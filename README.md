# reticulum-atak

An ATAK plugin that shows what a Reticulum mesh is doing with your traffic:
whether a message or file arrived, who it is queued for, how far away they are,
what fetching something will cost before you spend it, and whether the
propagation node is holding anything for you.

ATAK already draws the map, the chat and the markers. Over a delay-tolerant
mesh those can be misleading: a message ATAK reports as sent may be waiting
for a path, held at a propagation node, or crossing a LoRa link at a few
hundred bytes a second. This plugin makes that state visible to the person
holding the phone, in short lines they can act on.

## How it relates to its siblings

| | this repository | `urban-tak` | `microReticulum_Firmware` | `columba` |
| --- | --- | --- | --- | --- |
| Answers | *did it arrive, who is it queued for, how far, what will it cost* | *where can I walk, what is this address* | *how does it travel* | *the phone's Reticulum node* |
| Reticulum | **the whole subject** | must never depend on it | the implementation | the implementation |
| Ships as | signed APK, sideloaded into ATAK | signed APK | flashed firmware | APK |

The split is deliberate: `urban-tak` stays useful on any transport by never
depending on Reticulum, and the firmware repository shares no toolchain with an
Android plugin. The wire formats are the contract between repositories, pinned
by shared fixtures, not a reason to merge them.

## Status

Scaffolding. See `docs/Roadmap.md` for the order of work and
`docs/OpenDecisions.md` for what has to be settled before code.

## Building

Not yet. An ATAK plugin builds against the ATAK-CIV SDK, which TAK.gov
distributes to registered developers rather than through a public package
repository. `docs/Setup.md` covers the SDK, the bench device, and which ATAK
version the bench and releases each build against.

## Contributing

**One rule applies from the start:** nothing operational goes in this
repository. No real coordinates, callsigns, team details, node or destination
hashes, identities, keys or passphrases -- not in code, tests, fixtures, commit
messages or issue text. It handles identity and reachability, so it is held to
a stricter standard than a map plugin: **it may name the concepts, never the
fleet.** Test data is synthetic.

## Licence

Not yet chosen. See `docs/OpenDecisions.md`.
