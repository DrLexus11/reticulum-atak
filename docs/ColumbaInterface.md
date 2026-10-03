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
  `network.columba.app.mesh.BIND`, and no permission: a plugin cannot add one
  to ATAK's manifest (`OpenDecisions.md` 1).
- The plugin binds by action with an explicit package: `network.columba.app`
  first, then `network.columba.app.debug`, which the bench phones run.
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
    /** 1 for this document; -1 for a refused caller. The plugin refuses a version it does not know. */
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
    "is_command_post": null,
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
  says whether the propagation node in use belongs to one, and is `null` while
  Columba cannot tell -- the first implementation always sends `null`, because
  a command post's propagation node and its TAK node are different identities.
- **`carrier`** is the class of the next-hop interface, derived from the
  interface's type -- the class name Reticulum prints before the bracket -- and
  never from its declared bitrate (`CLAUDE.md`, interface completeness):
  `lora` (an RNode or KISS modem), `ble`, `tcp` (including backbone), `udp`,
  `auto`, `local` (a shared instance), `unknown`. There is no `wifi`: an
  interface's type cannot tell Wi-Fi from any other IP link, so it reports as
  the IP transport it uses. `interface` is the name as Columba shows it.
- **`path` / `hops`** come from Columba's path table at the moment of the
  snapshot. `hops` is `null` with no path.

## Fixtures

`fixtures/columba_mesh_v1.json` (in both repositories): snapshots that both
sides must parse to the same values -- a full one, one with no propagation node
and no paths, one with every optional field `null`. Columba parses each and
writes it back unchanged, so it can neither drop a field nor turn a `null`
into a value; the plugin parses each and checks the lines in its `expect`
block. The full snapshot carries `is_command_post: true` although Columba sends
`null` today: the fixture covers what a version 1 snapshot may hold, not what
the first implementation happens to produce. The `expect` block is the plugin's
-- the lines the panel shows for each snapshot.

## Order

1. This document and the fixture (reticulum-atak).
2. Columba: the service, the allow-list, the snapshot builder against the
   fixture, the watcher, `announce`, and the "allow ATAK control" setting
   (Columba pull request).
3. The plugin: bind, check the version, render the panel from snapshots;
   locate, open GeoChat, announce.

# Version 2: interfaces -- planned 2026-10-03

For the plugin's Interfaces page (Roadmap, "Pages"). Version 2 only **adds**:
v1's methods, fields and result codes are unchanged, the AIDL gains methods at
its end (AIDL transaction codes are positional, so appending keeps a v1 client
working), and `version()` returns 2. The plugin accepts 1 or 2 and shows the
Interfaces page only on 2.

## Snapshot additions

```json
{
  "v": 2,
  "interfaces_live": false,
  "interfaces_pending": true,
  "interfaces": [
    {
      "id": 3,
      "name": "<Columba's name for it>",
      "type": "TCPClient",
      "carrier": "tcp",
      "enabled": true,
      "online": true,
      "rx_bytes": 123456,
      "tx_bytes": 65432,
      "reason": null,
      "carries_command_post": true
    }
  ]
}
```

- One entry per **configured** interface (Columba's database: id, name, type,
  enabled), joined by name with the **running** stack's state (online, bytes,
  the stack's own one-line reason when it is down). A configured interface the
  stack does not run is `online: false`.
- `carrier` as in v1, from the type.
- `carries_command_post`: the interface is the next hop of the path to some HQ
  peer -- switching it off would cut the command post off.
- `interfaces_live`: switches apply at once (Columba's Kotlin backend).
  `false` on the Python backend, where they are staged; `interfaces_pending`
  says a staged change is waiting for Apply.

## Commands

```aidl
    /** Switch one configured interface. Result codes below. */
    int setInterfaceEnabled(long id, boolean enabled);
    /** Apply staged switches: restarts Columba's Reticulum (Python backend). */
    int applyInterfaces();
```

Both are refused like every command: `1 ERR_CALLER`, `2 ERR_CONTROL_OFF`
("allow ATAK control" off), `3 ERR_NOT_READY`. New codes:

- `5 ERR_WOULD_ISOLATE` -- Columba's guard, on the **resulting** set of
  interfaces: never switch off the one carrying the command post's path, never
  leave no interface online. Checked in Columba, so no caller can bypass it.
- `6 ERR_UNKNOWN_INTERFACE` -- no configured interface with that id.
- `7 OK_PENDING` -- accepted and staged; it takes effect on `applyInterfaces()`.
- `applyInterfaces()` with nothing staged returns `0 OK` and restarts nothing.

Every command is logged in Columba with its caller, as in v1. The plugin asks
for confirmation before Apply and says what it costs: "Restarts the mesh on
this phone (about 5 s); links drop and rebuild."

## Fixture

`fixtures/columba_mesh_v2.json`: a v2 snapshot with a live and a staged case,
an interface carrying the command post's path, and one configured but not
running -- both sides test against it, as for v1.
