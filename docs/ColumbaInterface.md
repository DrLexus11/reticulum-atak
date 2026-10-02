# Columba's mesh interface, version 1

The contract between Columba (the phone's Reticulum node) and this plugin.
Columba owns it and serves it; the plugin is a client. Both repositories carry
the same AIDL file and the same snapshot fixture, and both test against the
fixture -- the discipline the firmware and Columba already keep for
`tak_native_v1.json`. A change to either is a version bump, made in both
repositories in the same week.

Decisions behind it: `OpenDecisions.md` 1 (a bound service, a caller
allow-list, read plus named commands behind a setting) and 5 (propagation node).

## Why a new interface, not Columba's own

Columba already speaks to its Reticulum process through `IRnsCore` and its
siblings. None of them can be handed to ATAK: `IRnsCore` alone can export the
identity's private key, sign with it, and blackhole identities. The plugin gets
a separate interface, built on top of them inside Columba, that exposes only
what the plugin's features need.

## The service

- Declared by Columba, `exported="true"`, action
  `network.columba.app.mesh.BIND`, no permission (a plugin cannot add one to
  ATAK's manifest; `OpenDecisions.md` 1).
- Runs in Columba's app process, beside the TAK endpoint that already holds the
  member table and the propagation manager.
- **Every call checks its caller.** `Binder.getCallingUid()` gives the
  packages; each must be on the allow-list -- package name and the SHA-256 of
  its signing certificate. Version 1's list: `com.atakmap.app.civ` with TAK's
  store certificate, and `com.atakmap.app.civ` with the SDK's developer
  certificate (bench phones). Anything else gets `ERR_CALLER` and nothing more.
  Checked per call, not per bind: a binder handed on to another process must
  not carry the first caller's standing.

## AIDL

```aidl
package network.columba.app.mesh;

import network.columba.app.mesh.IColumbaMeshWatcher;

interface IColumbaMesh {
    /** 1 for this document. The plugin refuses a version it does not know. */
    int version();

    /** The mesh as this phone sees it: a snapshot, JSON, below. */
    String snapshot();

    /** Called with a fresh snapshot when it changes, at most once a second. */
    void watch(IColumbaMeshWatcher watcher);
    void unwatch(IColumbaMeshWatcher watcher);

    /** Commands. Each returns a result code. */
    int announce();
}

interface IColumbaMeshWatcher {
    oneway void changed(String snapshot);
}
```

Result codes: `0 OK`, `1 ERR_CALLER` (not on the allow-list), `2 ERR_CONTROL_OFF`
(Columba's "allow ATAK control" is off), `3 ERR_NOT_READY` (Reticulum not
running), `4 ERR_RATE_LIMITED`.

Commands added after PR F -- switch an interface, select the propagation node,
fetch a NomadNet page -- arrive as version 2, with their own codes. The
interface switch carries its guard in Columba: a switch that would leave no
path to the command post, or no interface up, is refused there.

**Announce is rate-limited, and not out of politeness.** Reticulum relays block
a destination that announces faster than their rate allowance, which costs the
node its name downstream -- the opposite of re-meshing fast. The minimum
interval is set from the relays' allowance when the command is built, and the
refusal says how long remains.

## Snapshot, version 1

JSON, so the plugin and its tests need no Parcelable classes from Columba.
Times are Unix milliseconds; hashes are lower-case hex; a field Columba does not
know is `null`, never omitted and never a guess.

```json
{
  "v": 1,
  "at": 1790000000000,
  "node": {
    "uid": "urtn-<destination hash>",
    "callsign": "...",
    "control": true,
    "running": true
  },
  "propagation": {
    "hash": "<propagation node destination hash>",
    "name": "...",
    "path": true,
    "hops": 2,
    "carrier": "lora",
    "is_command_post": true,
    "last_sync": 1789999990000
  },
  "peers": [
    {
      "uid": "urtn-<destination hash>",
      "callsign": "...",
      "role": "HQ",
      "heard": 1789999950000,
      "path": true,
      "hops": 3,
      "carrier": "ble",
      "interface": "<Columba's interface name>"
    }
  ]
}
```

- **Peers** are the team members Columba has heard (its TAK member table), not
  every destination on the mesh. `uid` is the ATAK contact UID the endpoint
  already uses (`urtn-` and the destination hash), so the plugin finds the
  marker and opens GeoChat with it directly.
- **The command post** is any peer whose role is `HQ` -- ATAK's own role, set
  on the command post's ATAK, so it needs no configuration of its own. The panel
  shows reachability as "a path to any HQ peer". `propagation.is_command_post`
  says whether the propagation node in use belongs to one.
- **`carrier`** is the class of the next-hop interface -- `lora`, `ble`,
  `wifi`, `tcp`, `udp`, `auto`, `rnode`, `unknown` -- derived from the interface
  type, not its name, and never from its declared bitrate (`CLAUDE.md`,
  interface completeness). `interface` is the name as Columba shows it.
- **`path` / `hops`** come from Columba's path table at the moment of the
  snapshot. `hops` is `null` with no path.

## Fixtures

`fixtures/columba_mesh_v1.json` (in both repositories): snapshots that both
sides must parse to the same values -- a full one, one with no propagation node
and no paths, one with every optional field `null`. Columba's tests build each
from a fake member table and path table and compare; the plugin's tests parse
each and check what the panel would show.

## Order

1. This document and the fixture (reticulum-atak).
2. Columba: the service, the allow-list, the snapshot builder against the
   fixture, the watcher, `announce`, and the "allow ATAK control" setting
   (Columba pull request).
3. The plugin: bind, check the version, render the panel from snapshots;
   locate, open GeoChat, announce.
