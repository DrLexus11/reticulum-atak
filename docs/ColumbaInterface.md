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

# Interfaces: an additive capability -- planned 2026-10-03

For the plugin's Interfaces page (Roadmap, "Pages"). **Additive, so not a new
version:** `version()` stays 1 and so does the snapshot's `"v"`, because a
version 1 client refuses anything else -- deploying a Columba that said 2 would
cut off every installed plugin. Instead:

- The snapshot gains optional fields, which a version 1 client ignores (it
  reads the fields it knows; org.json skips the rest).
- The AIDL gains methods **at its end**, so the transaction codes of the version
  1 methods are unchanged.
- A new `capabilities()` says what the service offers. Against a Columba that
  predates it, the call has no implementation and returns 0, so a newer plugin
  sees no capabilities and simply hides the Interfaces page.
- **The capability bit is the only trustworthy answer.** An older Columba's
  binder does not know the new transaction codes: it answers with an empty
  reply, and the generated proxy reads an empty reply as "no exception, 0".
  That is why `capabilities()` comes back 0. But it also means
  `setInterfaceEnabled` and `applyInterfaces` would come back 0, which is `OK`,
  for a switch that was never heard. The plugin therefore checks the bit before
  every such call and reports "this Columba cannot switch interfaces" without
  asking (`ColumbaMeshClient.command`). Every future appended method gets the
  same gate.

A **breaking** change -- a field changing meaning, a method changing signature
-- is what bumps `version()`; the plugin refuses a version it does not know.

## Snapshot fields

```json
{
  "v": 1,
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
  enabled), joined by exact name -- as Columba's own interface screen joins them
  -- with the **running** stack's state (online, bytes, the stack's own
  one-line reason when it is down). Configured but not running: `online: false`.
- `carrier` as in v1, from the configured type.
- `carries_command_post`: the interface is the next hop of the path to some HQ
  peer -- switching it off would cut the command post off.
- `interfaces_live`: switches apply at once (Columba's Kotlin backend).
  `false` on the Python backend, where they are staged; `interfaces_pending`
  says the configured state differs from what is running, waiting for Apply.

## Methods

```aidl
    // appended after announce():
    /** Bitmask of what this service offers: 1 = interfaces. 0 from a Columba that predates it. */
    int capabilities();
    /** Switch one configured interface. */
    int setInterfaceEnabled(long id, boolean enabled);
    /** Apply staged switches: restarts Columba's Reticulum where it cannot switch live. */
    int applyInterfaces();
```

Both commands are refused like every command: `1 ERR_CALLER`, `2
ERR_CONTROL_OFF` ("allow ATAK control" off), `3 ERR_NOT_READY`. New codes:

- `5 ERR_WOULD_ISOLATE` -- Columba's guard, on the **resulting** set: never
  switch off the interface carrying the command post's path, never leave no
  interface online. In Columba, so no caller can bypass it.
- `6 ERR_UNKNOWN_INTERFACE` -- no configured interface with that id.
- `7 OK_PENDING` -- accepted and staged; it takes effect on `applyInterfaces()`.
- `applyInterfaces()` with nothing staged, or where switches are live, returns
  `0 OK` and restarts nothing.

Every command is logged in Columba with its caller, as before. The plugin asks
for confirmation before Apply and says what it costs: "Restarts the mesh on
this phone (about 5 s); links drop and rebuild."

## Fixture

`fixtures/columba_mesh_interfaces.json`: snapshots with the interface fields --
a staged case with an interface carrying the command post's path, one configured
but not running, and a live case. Both sides test against it, as for
`columba_mesh_v1.json`.
