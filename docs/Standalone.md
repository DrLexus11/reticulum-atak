# The standalone plugin -- design and feasibility

**Status: proposal, 2026-11-08. Nothing here is built.** Post-demo work: none of
it touches the demo build or the phones it runs on.

**The direction (operator, 2026-11-08):** the plugin becomes standalone. It runs
its own Reticulum and LXMF stack, the same way Columba does, with Python inside
the plugin and no dependency on Columba. Today the plugin is a UI over Columba's
exported service (`OpenDecisions.md` 1, `ColumbaInterface.md`).

The first feature that needs this is **push-to-talk**: Columba's talk channels
in ATAK at the same fidelity. That covers lxpt floor control, the A and B
positions, banks, the channel table bound to rooms, and audio. The second is
**ATAK group chat over RRC**: GeoChat groups created, changed and used in ATAK,
with all of it carried by a room on an RRC hub.

This document answers whether that is feasible and how. It ends with the
decisions it needs from the operator (section 9).

**How to read the evidence.** Every claim carries one of three marks:

- **[V] verified**: read in a named source in this repository, a sibling
  repository, the ATAK-CIV SDK or a pipeline build log.
- **[R] reasoned**: follows from verified facts, but has not been observed.
- **[U] unknown**: the phase that measures it is named.

Paths such as `sdk/samples/...` are inside the ATAK-CIV SDK 5.5.1.8 bundle
(`Setup.md`). "Pipeline" means TAK.gov's third-party build service.

## 1. What blocks the direction, and what does not

1. **LXST-kt cannot ship in this plugin.** Columba's talk audio is built on it.
   Its licence now reads: "used under license for Columba. No derivatives or use
   outside Columba is permitted." [V] (section 7). The plugin needs its own talk
   audio. This is feasible, because lxpt frames carry bare codec frames
   (section 4), but it is real work. Until the authors give written permission,
   the plugin has no one-to-one LXST calls.
2. **Python in a pipeline build is unproven.** Chaquopy normally fetches
   packages with pip at build time, using a Python interpreter on the build
   host. Whether the pipeline has either is unknown [U]. A layout that needs
   neither is described in 3.3. Phase 0 settles this with a probe submission
   before any feature code depends on it.
3. **Columba's Kotlin backend cannot host a talk channel today.** It has no
   inbound links [V] (section 3). That decides the stack question towards
   Python, which is also the operator's stated route.
4. **Nothing found blocks the architecture.** TAK's own SDK samples show a
   plugin running its own service, in its own process, under its own manifest
   permissions [V] (section 2).
5. **Group chat over RRC is feasible, but a room is not access control, and a
   hub reads room text** [V] (section 6). Both need an operator decision, and
   both must be said in the UI.

## 2. Architecture

```text
ATAK's process                           the plugin's own process
(package com.atakmap.app.civ)            (package io.github.drlexus11.reticulumatak)
+------------------------------+         +----------------------------------------+
| plugin UI, Java, as today    |  AIDL   | StackService (foreground)              |
|  - Mesh / Interfaces pages   |-------->|  - Python RNS + LXMF (Chaquopy)        |
|  - Talk page, PTT keys       |<--------|  - talk: lxpt floor/relay, transport   |
|  - group chat bridge         | events  |  - talk audio: capture, codec, playout |
|    (contacts, CoT in/out)    |         |  - RRC client, LXMF                    |
+------------------------------+         |  - identity, interfaces, BLE           |
                                         +----------------------------------------+
```

The plugin APK is installed as its own package. ATAK loads the UI part of it
into ATAK's process, as today. The stack runs in a foreground service that the
same APK declares, in the plugin's own process.

### 2.1 What is verified

- **A plugin can declare a service that runs outside ATAK, with the plugin's
  permissions** [V]. In `sdk/samples/smsservice`, the manifest declares
  `READ_SMS`, `SEND_SMS` and `RECEIVE_SMS` beside an exported
  `SmsManagerService`. The service's own comment says: "This service runs
  completely devoid of all ATAK supplied classes. It also inherits the
  permissions from the AndroidManifest because it is a service."
- **The ATAK side binds to it** [V]. `SmsMapComponent` binds with
  `new Intent(pluginContext, SmsManagerService.class)` and
  `view.getContext().bindService(...)`. `helloworld` does the same for
  `ExampleAidlService` (`HelloWorldMapComponent`, around line 470).
- **A plugin can run a foreground service** [V]. The `helloworld` manifest
  declares `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS` and
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Its `NotificationService` calls
  `startForeground`. The drop-down starts it with `startForegroundService` on an
  intent whose package is the plugin's.
- **Runtime permissions are asked by an activity in the plugin's package** [V].
  See `smsservice`'s `PermissionActivity` and `helloworld`'s
  `PluginPermissionActivity`. Both are started with `FLAG_ACTIVITY_NEW_TASK`.
- **Native libraries in a plugin are supported** [V]. The SDK's
  `docs/Build_Environment_Changes.pdf` says a plugin with native libraries must
  ship at least `arm64-v8a` and set `extractNativeLibs`. This plugin's
  `build.gradle` already keeps `jniLibs.useLegacyPackaging`, "required for
  plugin loading".
- **ATAK supplies some libraries to code running inside ATAK, and plugins must
  not bundle them** [V]. That list includes androidx fragment, lifecycle and
  core, `kotlin-reflect` 2.1.10, okhttp and others (`Build_Environment_Changes.pdf`;
  `sdk/docs/dependencies.txt` pins coroutines 1.7.3). `helloworld`'s
  `NotificationService` notes that it "can never use notification compat".
- **Hardware keys can be seen from the plugin** [V]. `MapView.addOnKeyListener`
  exists in the 5.5.1.8 API (`main.jar`). How it behaves with a USB PTT device
  is [U] (phase 1).
- **Chat and CoT hooks exist in the API** [V], all in the 5.5.1.8 `main.jar`:
  - `CommsMapComponent.registerPreSendProcessor` and `registerCommsLogger`, which
    see outgoing CoT;
  - `sendCoTInternally`, which hands ATAK an event as if it had been received;
  - `ChatDatabase.addGroup` and `removeGroup`, and a database listener;
  - contacts with a `PluginConnector`, whose GeoChat lines ATAK hands to the
    plugin as an intent carrying `ChatManagerMapComponent.PLUGIN_SEND_MESSAGE_EXTRA`
    (`helloworld`, `HelloWorldDropDownReceiver` around lines 2667 and 3258).

### 2.2 What the pipeline is known to do

From this plugin's own 5.6.0 pipeline build log (October 2026) [V]:

- The pipeline downloads Gradle 8.13 from `services.gradle.org` and installs
  Android platform 35 and build-tools on demand.
- It puts TAK's Artifactory first and then adds the repositories our
  `build.gradle` names, Google, Maven Central and JitPack among them.
- It prints `CI variable not set. Skipping NDK installation`.
- It runs R8 with ATAK's ProGuard mapping applied (`-applymapping`), and then a
  Fortify scan, a dependency check and an SBOM.
- **Our build has never resolved a third-party runtime dependency in the
  pipeline.** It has none. So "JitPack and Maven Central work there" is not yet
  shown [U].
- **One hint of egress filtering.** The dependency check's own fetch from a
  `github.io` address returned `403 Forbidden` [V]. Whether pip could reach PyPI
  or GitHub is [U].
- **Nothing in the source rules forbids a service or permissions** [V]. The
  rules are in `Setup.md`: `assembleCivRelease`, takdev, a single-root zip, our
  own `-repackageclasses`, and the component activity. `takdevLint` passed on
  our builds. Whether the pipeline *flags* a plugin that asks for the microphone
  and Bluetooth is [U]; nothing documented says it does. Phase 0 submits one
  that does.

### 2.3 What is reasoned

- **The service is exported and checks its caller on every call** [R]. ATAK binds
  from its own UID, so `exported="true"` is needed, and then any app could bind.
  The gate is decision 1's, moved into the plugin: calling package and signing
  certificate on a short allow-list, namely ATAK-CIV with TAK's certificate and
  the developer ATAK. Checked per call, as `ColumbaInterface.md` already
  specifies.
- **Foreground service types** [R]. Use `connectedDevice|microphone`, as
  Columba's Reticulum service declares (`connectedDevice|microphone|location`,
  [V] Columba `rns-host` manifest). Avoid `dataSync`, which Android 15 caps at
  6 h a day (firmware `docs/TalkChannels.md` §10).
- **The microphone, and the while-in-use rule** [R, with an Android source].
  From Android 14, a foreground service that needs the microphone must be
  created while its app has a visible activity, or under a listed exemption
  ([Android: restrictions on starting a foreground service from the
  background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions)).
  The plugin's own package is never the visible app; ATAK is. Two routes fit the
  documented exemptions:
  - **(a)** The service hands the UI a `PendingIntent` that the plugin's process
    created, and ATAK (visible) sends it. The page lists "a `PendingIntent` that
    is sent from a different, visible app".
  - **(b)** A transparent activity in the plugin's package starts the service
    while it is visible.

  Phase 0 measures which works on the Samsung phone. Android 8.1 on the bench
  phone has no such rule.
- **Unloading the plugin in ATAK does not stop the service** [R]. The mesh lives
  until it is stopped explicitly. The setting "keep the mesh running when ATAK
  closes" is a decision (section 9).
- **The UI stays Java and never touches stack classes** [R]. ATAK supplies
  Kotlin 2.1.10 and coroutines 1.7.3 to code in its process [V]. Columba builds
  with Kotlin 2.3.20 and coroutines 1.11.0 [V] (`gradle/libs.versions.toml`).
  Stack code, Kotlin or Python, runs only in the plugin's process, where the
  APK's own copies apply, and the UI reaches it only through AIDL.
- **The release build is obfuscated and the debug build is not** [R]. Our
  ProGuard file repackages classes and applies ATAK's mapping [V]. Python calls
  back into Kotlin by name (Columba marks those classes `@ReflectivelyKept` [V]),
  so a missing keep rule fails only in the pipeline build. Every phase gate is
  therefore run on the pipeline build in the store ATAK, never only on a debug
  build.
- **ABIs** [R]. The plugin filters `armeabi-v7a`, `arm64-v8a` and `x86` [V]. The
  desktop's Waydroid runs ATAK 5.8 on x86_64 (`Setup.md`). Python must be built
  per ABI, so the filter becomes `arm64-v8a`, `armeabi-v7a` and `x86_64`, which
  is Columba's set [V].
- **`minSdk` rises from 21 to 24** [V]. Chaquopy requires 24: its documentation
  says so, and so does the string in the 17.0.0 Gradle plugin. ATAK core allows
  plugins a higher minimum (`Build_Environment_Changes.pdf`). This drops
  Android 5 and 6. Both bench phones are newer.

## 3. The stack: Python (the operator's route) or Kotlin

Columba has two backends behind one API (`rns-api`). One is
`rns-backend-py`: upstream Python RNS and LXMF under Chaquopy. The other is
`rns-backend-kt`: reticulum-kt and LXMF-kt.

### 3.1 Python under Chaquopy

| | Finding | Mark |
| --- | --- | --- |
| What the bench runs | Columba's bench builds use the Python backend; firmware from the phone was proven on it (firmware `CarriedIssues.md` #14) | V |
| Talk hosting | `RnsCore.acceptLinks`, which a phone hosting a talk relay needs, is implemented only by `PythonRnsCore`; the API's default fails "Inbound links are not supported on this backend" | V |
| Resources | outgoing and incoming resource progress FULL (`PythonCapabilities.kt`) | V |
| Interface changes | no hot reload: an applied change restarts RNS, 3-5 s, and links drop (`OpenDecisions.md` 1) | V |
| Size | measured from Columba's debug APK: shared Python assets 8.1 MB, then per ABI about 15 MB (arm64-v8a), 10 MB (armeabi-v7a) and 15 MB (x86_64). About **23 MB for arm64 alone** and **about 48 MB for the three ABIs**. Today's pipeline APK is 34 KB | V |
| Cold start | not measured; only the 3-5 s restart is on record | U, phase 0 |
| Licences | Chaquopy MIT ([LICENSE.txt](https://github.com/chaquo/chaquopy/blob/master/LICENSE.txt)); RNS and LXMF under the Reticulum License; ble-reticulum MIT | V |
| Licences, continued | CPython (PSF), OpenSSL (Apache-2.0), `cryptography` (Apache-2.0 or BSD), u-msgpack (MIT) | R, to be listed from the built SBOM |

### 3.2 Building it in the pipeline

Columba's Python module needs three things at build time [V], from
`rns-backend-py/build.gradle.kts`:

1. **The Chaquopy Gradle plugin and Python runtime.** These are Maven artifacts:
   `com.chaquo.python:gradle:17.0.0` and `com.chaquo.python:target:3.11.14-0`,
   in the local Gradle cache. That they resolve through the pipeline's
   repositories is [U].
2. **pip at build time.** It installs `git+https://github.com/...` for the RNS,
   LXMF and ble-reticulum forks (all three are public repositories [V]), plus
   `cryptography` and `u-msgpack-python` from package indexes. Network egress in
   the pipeline is [U], and the 403 above is a warning sign.
3. **A build-host Python 3.11 on `PATH`**, which pip needs. The module's comment
   says so. The pipeline image is [U].

### 3.3 A layout that needs none of the unknowns

- **Vendor the pure-Python packages as source** under `app/src/main/python/`:
  RNS, LXMF, ble-reticulum and u-msgpack, pinned to the same commits as Columba.
  There is no pip step for them.
- **Drop `cryptography`.** RNS then falls back to its internal pure-Python
  primitives. `RNS/Cryptography/Provider.py` selects `PROVIDER_INTERNAL` when
  `cryptography` is absent, and the package ships `pure25519` [V].
  - The cost is CPU, for Ed25519 and X25519 in pure Python [U]. Phase 0 times
    sign, verify and link setup on the Nexus, the slowest phone.
  - lxpt signs every CLAIM and RELEASE, so this lands on the key-up path.
- **Turn off bytecode compilation** (`pyc { src false; pip false; stdlib false }`),
  so no build-host Python is needed [R]. Chaquopy's documentation ties the build
  Python to pip, the static proxy and bytecode compilation.

If the probe shows that pip and a build Python do work in the pipeline, keep
Columba's pip layout, with `cryptography`. If they do not, use this layout.
Either way, the vendored forks are public code, carried here under their own
licence notices.

### 3.4 Kotlin: reticulum-kt v0.0.22 and LXMF-kt v0.0.14

Both are MPL-2.0 [V], by the same upstream authors as LXST-kt.

- **For it:**
  - small: about 1.7 MB of jars in the Gradle cache [V], against Python's 23 MB
    or more;
  - live interface changes, and battery tuning [V] (`NativeCapabilities.kt`);
  - no build-time Python.
- **Against it today:**
  - no `acceptLinks`, so it cannot host a talk relay [V];
  - refuses to send to a PLAIN destination: "Cannot send to a PLAIN
    destination" [V] (`NativeRnsBackendImpl`);
  - no resource progress [V];
  - request Resources only ever compressed, which our boards refuse [V]
    (firmware `CarriedIssues.md` #14);
  - telemetry collector EXPERIMENTAL [V];
  - the fleet has never run it.
- **Interfaces it has** [V]: Auto, TCP client and server, UDP, BLE (an Android
  driver), RNode (`NativeInterfaceFactory`).
- **Licence risk** [R]. Upstream withdrew LXST-kt's MPL grant on 2026-11-05. If
  the same happened to reticulum-kt, we would be pinned to the last MPL
  versions. Python RNS is the reference implementation, and our forks follow its
  releases.

### 3.5 Recommendation: Python, as the operator stated

Python is recommended because talk hosting works only there, it is what the
fleet and the boards' interop were proven on, and the operator chose it.

Kotlin is not recommended now. It would close the gaps above only with work in a
library that is not ours. Revisit Kotlin if phase 0's size or start-time gate
fails.

**Which Python layout** (pip, or vendored with internal crypto) is decided by the
pipeline probe in phase 0.

**Taking Columba's Python backend** [V for the facts, R for the plan]:

- **Copy, with MPL headers kept:**
  - `rns-api`, about 7,100 lines;
  - from `rns-backend-py` (12,900 lines with its Python), only what phase 0
    needs: the runtime, core and AutoInterface;
  - later, the BLE and RNode bridges from `rns-host` (2,750 and 1,790 lines).
- **Strip:**
  - Hilt;
  - the `api(libs.lxst.kt)` edge, with `PythonRnsTelephony`, the telephony parts
    of `ChaquopyRnsBackend`, and `CallState`, all of which import LXST.
- **The alternative** is a thin Python bridge written for this plugin. It is
  smaller, but it loses the hardening Columba carries. That is a decision
  (section 9).

## 4. What talk needs, and where each piece comes from

| Piece | Today | Licence | In the plugin |
| --- | --- | --- | --- |
| Wire, floor and relay cores | lxpt Kotlin (`com.github.DrLexus11:lxpt`, Columba pins v0.2.1, latest v0.3.0); stdlib only | Apache-2.0, public [V] | depend on it (vendor the Kotlin sources if JitPack fails in the probe) |
| Talk service | Columba `talk/lxpt/`: `LiveTalkService`, `RnsTalkTransport`, `TalkClient`, `RelayHost`, `StaleSpurts`, `TalkChannelKeys`, `TalkPorts` (about 2,600 lines), already behind the `TalkTransport`, `TalkAudio` and `TalkIdentity` seams [V] | MPL-2.0 (Columba) | copy into the service process; keep the seams |
| State and model | `TalkStateMachine`, `TalkModel`, `PttInput`, `QuickReplies`, `ChannelTableStore` (versioned JSON, v1) [V] | MPL-2.0 | copy |
| Talk screen | `TalkScreen`, `TalkControls` and others, in Jetpack Compose [V] | MPL-2.0 | rewrite as an ATAK page in Views (Java), beside Mesh and Interfaces; one short line per state |
| **Audio** | `LxstTalkAudio` over LXST-kt's `TalkLine`: capture, Opus or Codec2, pre-roll, permit tone, `StaleBurstGate`, playout prebuffer [V] | **LXST-kt: not usable** (section 7) | **new**: Android capture and playout (or Oboe, Apache-2.0), libopus (BSD-3), Codec2 (LGPL-2.1, dynamically linked) |
| Identity for CLAIM signatures | `TalkIdentity` over the node's identity [V] | -- | the plugin's own identity (section 5) |
| Channel table bound to rooms | positions bound to a hub and room, persisted [V] | -- | the same; the room is shared with group chat (section 6) |
| PTT keys | Columba `PttInput`; the USB device is HID [V] | -- | `MapView.addOnKeyListener` in ATAK [V exists, U behaviour] |

**Why talk audio without LXST is possible** [V]. lxpt's FRAME payload is one
codec frame, sealed: "one codec frame (talker to relay to listeners)". Profiles
are named by LXST's ids, `0x10`-`0x50`: Codec2 700C, 1600 and 3200, and Opus 8
and 16 kbit/s (lxpt `spec/wire-v1.md` §2 and FRAME). No LXST framing travels.
Interop with Columba therefore needs the same codec settings, not LXST code. The
spec already fixes Opus at `max_packet` 197 bytes.

**[U] Codec2 framing.** Whether LXST's Codec2 frames add a mode header that
Columba's receiver expects must be established from the wire, by capturing
Columba's frames, not by reading LXST source (section 7). The phase 1 gate
includes plugin-to-Columba audio in both directions.

### 4.1 Carriers

This applies the interface-completeness rule (`CLAUDE.md`). Today's state is
from firmware `docs/TalkChannels.md` §8 and T3a [V].

| Carrier | Talk in Columba today | The plugin |
| --- | --- | --- |
| Wi-Fi LAN, Auto | built for it | phase 1 target; **only one app per phone can own Auto** (section 5) |
| TCP to a board | built for it; first leg, Opus HQ | phase 1 target |
| BLE phone-to-board | refused, `No talk path`, until T7 | refused, the same as Columba; the interface itself comes in phase 3 |
| BLE phone-to-phone | refused until T5b/T7 | refused, the same |
| LoRa (through a board) | notices only, never audio | the same rule; the plugin never keys audio onto LoRa |
| HaLow | passes the IP rule, untested | the same, untested |
| A shared instance | refused: the next hop beyond it is invisible | not used: the plugin never joins a shared instance (section 5) |

The talk path rule decides by the next hop's interface type and hop count
(`TalkPaths.isIp`), never by declared bitrate. This works only on the plugin's
own stack, not through someone else's [R].

## 5. Coexistence with Columba on one phone

### 5.1 Facts

- **The two apps cannot read each other's files** [R, Android's sandbox]. Each
  APK is its own UID with its own private storage. Neither can read the other's
  identity.
- **Python RNS shares an instance by default** [V]. `share_instance` defaults to
  yes, over AF_UNIX on Android (`platformutils.use_af_unix()`: Linux or
  Android). Columba renders `shared_instance_type = tcp` on ports 37428/37429
  when it shares (`RnsConfigFile.kt`).
- **Columba joins any master it finds** [V]. Its Python backend joins a master
  on TCP 37428 whenever one answers (`SharedInstanceProbe.shouldShareInstance`,
  unless `preferOwnInstance` is set). If the plugin hosted a shared instance,
  Columba would silently become its client, depending on which app started
  first. The reverse also holds.
- **AutoInterface allows only one owner per phone** [V], from
  `RNS/Interfaces/AutoInterface.py`. The discovery sockets set `SO_REUSEADDR`
  and `SO_REUSEPORT`, so two instances both bind them and both hear discovery.
  The data socket (port 42671, through `socketserver.UDPServer`, no reuse) can
  be held by only one. A second instance fails to bind it.
  - Columba detects this and skips Auto, by a test bind on 29717
    (`SharedInstanceProbe.isAutoInterfaceUsable`).
  - RNS's data port is 42671, and 29717 is its unicast-discovery port. The probe
    should be checked against that before the plugin relies on Columba stepping
    aside.
- **Peers could not tell the two apps apart on Auto** [R]. Both would answer
  from one link-local address.
- **BLE** [U]. Two apps serving the same GATT service UUID on one phone is
  untested. Treat it as one owner.
- **TCP to a board** [R]. Both apps can connect, but that costs two of the
  board's client slots and puts two identities on one phone.
- **USB (RNode)** [R]. Android grants a USB device per app, so there is one
  owner.
- **CoT** [V]. Columba's TAK bridge takes ATAK's CoT on a local endpoint
  (`CotEndpointManager`, `CotPortConflict`). Two bridges on one phone would
  double every line.

### 5.2 Proposal

- **One app owns the mesh on a phone.** On an ATAK phone that is the plugin.
  Columba stays the node for people without ATAK.
- **The plugin never hosts or joins a shared instance.** It always writes
  `share_instance = No`.
- **At start, the plugin checks for another Reticulum on the phone.** It probes
  TCP 37428, the Auto data port and Columba's CoT endpoint. If it finds one, it
  starts without Auto, BLE and CoT, and says so in one line:
  `Mesh: Columba active · Auto, BLE, chat off`.
- **Identities are separate, and never shared while both run.**
  - [R] One identity announced from two running stacks splits delivery:
    whichever announce a peer heard last gets the traffic.
  - [R] Ratchet keys are per instance, so messages encrypted to one instance's
    ratchet cannot be opened by the other.
  - The plugin creates its own identity on first start. Columba's is never
    touched.
- **No device may lose its identity.** Two traps are specific to the plugin [V]:
  - Uninstalling the plugin deletes its private storage, and the manifest sets
    `allowBackup="false"`.
  - Moving a phone between a development-key build and a pipeline build means
    uninstalling first (`Setup.md`, "The bench device").

  So identity export is part of phase 0, not an afterthought:
  - the export is made at creation, encrypted under a passphrase typed on the
    phone (never passed in);
  - it is written where an uninstall leaves it (ATAK's shared tree survives,
    `Setup.md`);
  - an install that finds an export asks to import it, rather than silently
    creating a new identity.

  The location and the passphrase rule are decisions (section 9).
- **Moving Columba's identity into the plugin** is allowed only as a move:
  exported from Columba, which then stops using it. This is an operator
  decision.

## 6. ATAK group chat over RRC

**The requirement (operator, 2026-11-08):** "able to send geochat messages in a
group, creating, and modifying them and all works through RRC". This is a phase
of its own, after PTT parity (section 8, phase 2).

### 6.1 The two models

**ATAK GeoChat groups** [V], from the SDK's `docs/TakChatSpec.odt`:

- **Local.** Groups are kept locally on each device.
- **Unicast.** Messages to a group are unicast to each member. The exceptions
  are "All Chat Rooms" (LAN multicast) and "All Streaming" (via a server).
- **A group message names the group and its members.** `__chat` carries the
  group's UID (`id`) and its display name (`chatroom`). `chatgrp` lists the
  members as `uid0..uidN`, or a `hierarchy` element is used for nested groups.
- **Groups appear on receipt.** A member's ATAK creates the group when its first
  message arrives.
- **The creator maintains membership.**
  - Adding a member sends `[UPDATED CONTACTS]` to all members.
  - A removed member gets `[KICKED FROM GROUP]` with the new hierarchy.
  - A deleted sub-group sends "Deleted group: <name>" with a `deleteChild`
    attribute.
  - `groupOwner="true"` makes recipients replace the hierarchy rather than merge
    it.
- **Deletion is local.** Anyone may delete a group; a group they did not create
  is deleted only on their own device.

**RRC rooms** [V], from firmware `docs/RRCRequirements.md` §5-§8:

- **A room is created by JOIN.** Joining a missing room creates it. A room is
  deleted when its last member leaves. Membership belongs to a link session.
- **Names are per hub.** A room name is unique only on its hub, and compared
  without case. Columba keys a room as hub plus name (`TalkRoom`).
- **Room content.** MSG, NOTICE and ACTION go to every current member,
  **including the sender**. The hub keeps the client's message id, timestamp
  and body, and stamps the sender from the link identity. The nickname is
  display only.
- **Our firmware hub has no keys, invites, bans or operators** (§13, deferred).
  Anyone who can reach the hub and knows the name can join.
  - rrcd-style hubs, and Eridanus's own hub, do have invite-only, keys and bans
    [V] (Eridanus `HubRoomManager`).
- **Bridged rooms.** These are provisioned at boot, and changing the list needs
  a reboot. They mirror room lines to absent members over LXMF, using
  `rrc.bridge/1` fields with the RRC message id (§12c,
  `BridgeClientContract.md` §3). Ephemeral rooms keep no history.

### 6.2 The binding

One record per ATAK group, kept by the plugin and copied to every member. It
holds:

- the ATAK group UID, name, owner UID and member UIDs;
- the hub (destination hash) and room name;
- whether the room is bridged;
- the talk position bound to it, if any.

**Room name** [R]. Use the group name, normalised, plus a short tag from the
group UID, for example `alpha-3f2a`, so two groups called "Alpha" on one hub
stay two rooms. Keep it under about 20 characters: the firmware hub lists all 16
rooms only up to roughly that length (§12a [V]).

**The hub** [R]. It is chosen at creation. The default is the hub this phone is
connected to. Columba's RRC client holds one hub at a time [V]
(`roomStandingOf`, `OTHER_HUB`). One client link per hub, or one hub for all
groups, is a decision.

### 6.3 Create and modify, side by side

| ATAK action | What the plugin does | RRC | Mark |
| --- | --- | --- | --- |
| Owner creates a group (name, members) | sees it (the `ChatDatabase` listener, or its first line); asks once "Room on <hub>?"; writes the binding | the owner JOINs, which creates the room | V for the hooks, R for the flow |
| -- | sends the binding to each member as an **LXMF DM** (`rrc.group/1` in custom fields, like `rrc.bridge/1`): end-to-end, store-and-forward, reaches absent members | -- | R |
| Member receives the binding | creates the group in ATAK (`ChatDatabase.addGroup`, or an injected group line carrying `chatgrp`, as ATAK itself does) and JOINs | the member JOINs | R |
| Owner adds a member | ATAK sends `[UPDATED CONTACTS]`; the plugin sends the new member the binding, and the others the new list | the new member JOINs | R |
| Owner removes a member | ATAK sends `[KICKED FROM GROUP]`; the plugin tells the removed member, whose plugin PARTs | **on our hub, nothing stops a re-JOIN**: removal is advisory. On an rrcd-style hub the owner's plugin can use invite-only rooms and bans | V hub behaviour, R flow |
| Rename | the binding is keyed by group UID, so the room keeps its name and only the display changes | none | R |
| Owner deletes the group | ATAK's "Deleted group" line; plugins PART and drop the binding | the room dies when empty (ephemeral) or persists (bridged) | V hub, R flow |
| A member deletes a group they did not create | local only, as ATAK's rule: that member PARTs | that member PARTs | V rule |

**Only the owner's changes count** [R]. The plugin applies a membership change
only when it is signed by the group owner's identity. A DM is signed by its
sender; a room line is attributed only by the hub. This follows ATAK's own rule
that "the group creator should have the final say".

### 6.4 A line, both ways

**GeoChat to the room** [R]:

1. ATAK sends a group line. The plugin receives it through the members'
   `PluginConnector`s, or sees it in `PreSendProcessor`.
2. ATAK sends one copy per member, so the plugin deduplicates by ATAK
   `messageId` and sends **one** RRC MSG.
3. The body is the line's text, so stock clients (NomadNet, Columba Groups) read
   it.
4. The RRC id is the first 8 bytes of ATAK's 16-byte message UUID. The envelope
   id is exactly 8 bytes [V].
5. Receipts are not sent to rooms. Columba already drops room receipts as two
   thirds of a room's airtime [V] (`CotEndpointManager`).

`PreSendProcessor` returns nothing, so it cannot suppress ATAK's own send [V].
Members must therefore be contacts whose default connector is the plugin's, or
ATAK also tries IP [R]. How ATAK fans a group line out over plugin connectors is
[U], measured in phase 2.

**The room to GeoChat** [R]:

1. For each MSG in a bound room, build a group GeoChat event:
   - `chatroom` is the group name and `id` is the group UID, with
     `parent="UserGroups"` and `groupOwner="false"`;
   - `chatgrp` lists the members;
   - the sender is the UID derived from the identity the hub stamped, which is
     authoritative, with the nickname as callsign only if no contact is known;
   - the message id is a UUID derived from (hub, room, RRC id, sender), so every
     phone derives the same one.
2. Hand it to ATAK with `sendCoTInternally` [V exists].
3. Room members without ATAK (Columba Groups, NomadNet) appear with their nick
   and a short hash.

**No duplicates, no loops:**

- **Our own echo.** The hub sends our line back to us [V]. The plugin knows the
  RRC ids it sent and does not inject those [R].
- **Bridged copies.** A copy that arrives later over LXMF carries the same RRC
  message id in `rrc.bridge/1` [V], and is merged by that id [R], as Columba's
  `GroupBridgeReceiver` does.
- **Injected events.** These are not sent again. The plugin keeps the set of
  message ids it injected, and its send path ignores them [R].
- **One conversation, one path:**
  - a group bound to a room goes over RRC only;
  - a DM goes over LXMF only, end-to-end;
  - "All Chat Rooms" and team chat stay on today's team fan-out over LXMF. That
    is the same codec as Columba's `service/tak/` and the command post's CoT
    bridge, pinned by the shared fixtures, and is phase 3.
- **One bridge per phone.** If Columba's TAK endpoint is active, the plugin
  bridges no chat (section 5.2).
- **The command post** sees a group only if its client joins the room. That is a
  decision; the command post's bridge is not changed by this design.

### 6.5 One room for the group's text and its talk channel

This is already Columba's model [V]:

- a `TalkChannel` bound to a `TalkRoom` (hub and room) *is* that room, and "two
  positions bound to one room (on one hub) are one channel";
- the binding is persisted in `ChannelTableCodec` v1, as `hub` and `room` per
  position;
- the talk screen's text panel shows that room's lines (`GroupsTalkText`);
- lxpt keys a channel by (hub, room) (`TalkChannels.md` §1).

In the plugin [R]:

- the group binding (6.2) names the hub and room;
- binding a PTT position to the group stores the same `TalkRoom`;
- the group's GeoChat and its talk channel are then one room with one name;
- removing a member from the group also removes them from the talk channel's
  roster once lxpt keys arrive (T8).

Until T8, talk keys are derived from the channel id (T3a, bench only [V]). So a
removed member can still listen, which is the same limit as the room text.

### 6.6 Limits

- **Body size.**
  - Our firmware hub caps a message body at 280 bytes so that the whole envelope
    fits one 431-byte link MDU. It advertises this in WELCOME, and refuses
    larger rather than splitting [V] (§5, §7).
  - Eridanus's default is 350 [V].
  - A longer GeoChat line is either split into marked parts (`1/2`, `2/2`) or
    refused in one line, `HELD · too long for room · 280 B`. That is a decision.
- **No Resources on our hub** [V]. WELCOME advertises Resource support off.
  Nothing larger than one packet crosses.
- **rrcd's RESOURCE_ENVELOPE** [V] (type 50; body: id, kind, size, sha256,
  encoding; read from rrcd's `resources.py`):
  - rrcd accepts a Resource only when an envelope announced it, up to
    `max_resource_bytes` (256 KiB by default).
  - Kind `notice` is decoded and forwarded to room members as a NOTICE packet.
  - Kind `motd` is logged.
  - Kind **`blob` is received and logged, and forwarded to nobody**.
  - Eridanus's client shows any kind as notice text.

  **So no hub we use relays an attachment to a group.**
- **Attachments therefore travel as files, not room traffic** [R].
  - The room gets one line: `FILE · <name> · <size> · <sender>`.
  - The file goes by the existing TAK file offer, cost shown before fetching
    (Columba `TakFileOffer`; this plugin's roadmap), or as LXMF to each member.
  - A real attachment path through RRC would need hub work (a forwarded blob
    kind) on rrcd and on our firmware. That is not proposed here.
- **History.** An ephemeral room gives absent members nothing [V]. Only
  provisioned, bridged rooms (at most 4 rooms of 16 members on our hub [V])
  reach absent members, through LXMF.
  - An ATAK group made at runtime is ephemeral unless it is mapped onto a
    provisioned room.
  - The plugin should say so in the group's first line:
    `ROOM · live only · absent members miss lines`.
- **Rate and size of the hub** [V]. Our hub's starting limits are 8 sessions,
  4 rooms per session, 16 rooms and 60 messages per minute per session (§7;
  profiling may lower them). A group bigger than the session cap does not fit
  one board's hub.

### 6.7 Trust

- **The hub reads room text** [V]. RRC encrypts each client's link to the hub;
  it is "not end-to-end encryption among room participants: the hub sees content
  in order to relay it. Operators and product material must say this plainly"
  (§8).
- **The hub asserts who said what** [V]. Senders are stamped from link
  identities by the hub. Bridged copies are "asserted by the hub"
  (`BridgeClientContract.md` §1).
- **LXMF DMs are end-to-end and signed by their sender.**
- **ATAK users expect GeoChat groups to be unicast per member** (the spec).
  Over RRC, every line passes through one hub.

**Proposal: mark the difference where the operator reads it.**

- **The group's first line, injected once when it binds and again whenever the
  hub changes:** `ROOM via <hub name> · hub reads text`.
- **The plugin's group list** shows `HUB` for a room and `E2E` for a DM thread,
  state first.
- **Optionally, a suffix on the group's name** in ATAK (`Alpha ·hub`). It is
  visible in every chat list, at the cost of the name the creator typed. This
  is a decision.
- **Nothing that must stay end-to-end goes to a group.** The binding DM, the
  membership changes and any future room keys travel by LXMF only.

## 7. Licensing

**This repository has no licence yet** (`OpenDecisions.md` 3) [V]. A standalone
plugin ships other people's code, so the choice is now due. What it would carry:

| Component | Licence | Obligation |
| --- | --- | --- |
| RNS, LXMF (vendored or pip) | Reticulum License [V] (`Reticulum/LICENSE`) | keep the notice. **Two use restrictions:** no system "which includes amongst its functions the ability to purposefully do harm to human beings", and no use in AI or ML training datasets |
| Files copied from Columba | MPL-2.0 [V] | file-level: those files stay MPL with their headers, and source stays available (this repository being public satisfies that) |
| lxpt | Apache-2.0 [V] | notice |
| Chaquopy | MIT [V] | notice |
| ble-reticulum | MIT [V] | notice |
| CPython, OpenSSL, libopus, Oboe | PSF, Apache-2.0, BSD-3, Apache-2.0 | notices [R] |
| Codec2 | LGPL-2.1 [V] (as LXST-kt's README states) | link dynamically; let the user replace the library [R] |
| Eridanus RRC client (phase 2) | MPL-2.0 [V] | as Columba's files |

**The Reticulum License and ATAK** [R, a counsel question]. The plugin is for
disaster response. ATAK is also used by armed forces. With RNS inside the
plugin, the "harm" clause binds the plugin directly, not through Columba.
Columba's TAK bridge already puts RNS beside ATAK, so the programme has a
position. A public plugin makes that position public. Flag it; do not resolve
it here.

**LXST-kt, a blocker for its use** [V]:

- **Terms.** `DrLexus11/LXST-kt` (private since 2026-11-07; LICENSE on `main`
  since PR #9) and upstream `torlando-tech/LXST-kt` (commit `b399d6c`, "Change
  license to LXST Copyright 2026") both read: "LXST Copyright 2026 Mark Qvist.
  This implementation of LXST is derived from the LXST Python implementation,
  and used under license for Columba. No derivatives or use outside Columba is
  permitted."
- **Python LXST is CC BY-NC-ND 4.0.**
- The programme follows the authors' current terms (PR #9).

What a public plugin may do with LXST-kt:

| Use | Allowed? | Why |
| --- | --- | --- |
| Depend on an unmodified binary (the cached JitPack artifact, or a release) | **No** | the plugin is "use outside Columba" |
| Vendor its source, modified or not | **No** | a derivative, outside Columba, and published |
| Use our modified `impr` builds | **No** | the same, and a derivative |
| Talk to Columba's LXST-kt over the air | **Yes** [R] | interoperating with a running program over a wire format is not using its code. lxpt FRAMEs carry bare Opus or Codec2 frames (section 4) |
| Reimplement LXST call signalling for one-to-one calls | **Counsel** | upstream's author has said machine-assisted ports are derivative works. Whether an implementation of the protocol written from the wire is one is a legal question |
| Versions published under MPL-2.0 before 2026-11-05 | **Counsel** | MPL grants for code already received are normally not revocable, but the programme has chosen to follow the authors' current terms |

**The way through, for the operator to choose:**

- **(A) Recommended.** Write the talk audio in the plugin: Android audio or
  Oboe, libopus and Codec2, written without reading LXST-kt or Python LXST
  source. Talk needs no LXST protocol, only codec frames. One-to-one calls stay
  out of the plugin.
- **(B)** Ask the authors for written permission to use LXST-kt in the plugin.
- **(C)** Take counsel on the MPL-era versions.

(A) does not need (B) or (C).

**Columba's code in a public repository** [V]. Columba has been private since
2026-10-16. MPL lets its files be published, and most talk files were written by
this programme. Publishing them is still a choice about privacy, not about
licence. Each file's authorship is checked as it is copied.

## 8. Phases

Every gate is measured on the **pipeline build in the store ATAK** as well as
the bench build. Bench facts [V]:

- the Nexus runs developer ATAK 5.5.1.8 on Android 8.1 (bench builds, debug
  key);
- the Samsung phone runs store ATAK 5.6.0.12 and takes pipeline builds;
- Waydroid on the desktop runs ATAK 5.8, x86_64.

Waydroid is for UI and IP only: it has no microphone or BLE path worth measuring
[R].

### Phase 0 -- spike: the plugin's service starts the stack and lists Auto peers

**Pipeline probe, first.** One throwaway zip, read through its build log:

1. a service with the foreground types and permissions above (microphone,
   Bluetooth, multicast, notifications);
2. Chaquopy with one `git+https` pip package and one package from an index;
3. the vendored, pip-free layout of 3.3;
4. lxpt from JitPack.

This decides the Python layout (3.2 or 3.3) before code depends on it.

**Files:**

- `app/build.gradle`: Chaquopy, `minSdk 24`, ABIs `arm64-v8a`, `armeabi-v7a`,
  `x86_64`; the Kotlin plugin for service code; repositories;
- `settings.gradle`: plugin management;
- `app/src/main/AndroidManifest.xml`: `StackService`, permissions, foreground
  types, a permission activity;
- `app/src/main/python/`: vendored packages, or the pip block;
- new `stack/` sources: `StackService`, the AIDL for the stack (shaped like
  `ColumbaInterface.md`'s snapshot so the Mesh page changes little),
  `PermissionActivity`, identity export and import;
- `proguard-gradle.txt`: the user section, keep rules for Chaquopy and every
  class Python calls;
- `MeshPanel` and `MeshSession`: read the plugin's own stack;
- `README.md` and `Setup.md`: build notes;
- a third-party notices file.

**Gate (hardware):**

- the service starts from the ATAK pane on all three ATAKs, and its foreground
  notification shows;
- on the Samsung phone, the service starts with the microphone type through
  route (a) or (b) of 2.3, and the log shows no "started from background can not
  have ... microphone access";
- **cold start to RNS ready**, 10 runs per phone, p50 and p95 recorded;
- **APK size** per ABI;
- **memory**: the plugin process's RSS after 10 min, and ATAK's own memory
  unchanged within noise;
- Auto peers listed and matching what a LAN peer's `rnstatus` shows;
- with Columba running: the plugin says so in one line, starts without Auto, and
  does not crash; **Columba's identity hash is unchanged afterwards**;
- the plugin's identity survives an `install -r` upgrade, and an export and
  import round-trips through an uninstall;
- if pip-free: Ed25519 sign and verify, and link setup time, on the Nexus;
- ATAK still loads, unloads and reloads the plugin cleanly.

**Carriers:** Auto on Wi-Fi only; everything else untested. **Risks:** the
pipeline refuses the build or cannot fetch; obfuscation breaks Python callbacks
only in the pipeline build; Android 14 refuses the microphone service.

### Phase 1 -- PTT parity

**Files:**

- `talk/` (Kotlin, service process): Columba's `talk/lxpt/` and state machine,
  copied without `LxstTalkAudio`;
- `talk/audio/`: the new `TalkAudio`, capture and playout plus Opus and Codec2.
  The pipeline skips the NDK, so native code is either prebuilt `.so` files
  committed with their build recipe or a probe shows the NDK can be installed
  [U];
- talk commands and state in the stack AIDL: `key`, `release`, `select`, state
  flows;
- a Talk page in the ATAK pane: banks, the A and B positions, talker, heard
  count, one line per state;
- the PTT key listener;
- the channel table, stored as `ChannelTableCodec` v1.

**Gate:** Columba's T3b-gate and T4 numbers, on the plugin.

- 100 spurts of 10 s with at least 99% of frames delivered to every listener.
- Mouth-to-ear p95 at most 400 ms; key-up to permit p95 at most 400 ms.
- 50 simultaneous key-ups give exactly one talker.
- **Plugin to Columba and Columba to plugin on one channel, Opus HQ and Codec2**
  (the cross-implementation check that section 4 makes necessary).
- ATAK in the background with the screen off: 30 min listening with no gap over
  1 s.
- The microphone opens from an ATAK key-up and from a hardware key, on the Nexus
  and the Samsung phone.
- The Talk page at large text.

**Carriers:** Auto and TCP to a board, proven. BLE and LoRa are refused as in
Columba and say so. HaLow is untested.

### Phase 2 -- ATAK group chat over RRC

**Files:**

- `rrc/`: an RRC client in the service process (Eridanus's, MPL), one link per
  hub;
- `groups/`:
  - the binding store;
  - the `rrc.group/1` DM codec, with fixtures, specified in the firmware
    repository first like every wire format (`CLAUDE.md`);
  - the bridged-copy merge (`rrc.bridge/1`);
- ATAK side:
  - contacts with the plugin's connector;
  - the GeoChat send hook and its dedupe;
  - injection with `sendCoTInternally`;
  - the `ChatDatabase` listener;
- a Groups section on the pane, showing `HUB` and `E2E`.

**Gate:**

- a group created on one phone appears on two others, and lines flow both ways
  over a firmware hub and over rrcd;
- add, remove, rename and delete each behave as the table in 6.3 says;
- 100 lines with no duplicates in ATAK and no echo loops, including with a
  bridged room delivering the same lines over LXMF;
- a line over the size cap behaves as decided;
- a file offered in a group arrives by the file path;
- the trust line shows on every bound group;
- the same room carries the group's text and its talk channel (phase 1's gate,
  rerun on a bound room).

**Carriers:** whatever reaches the hub: Auto and TCP proven. A hub reached over
LoRa works for text (the hub's 280 B fits the MDU [V]), and is measured.

### Phase 3 -- the rest of chat and the carriers

- **DMs and team chat over LXMF**, replacing Columba's TAK bridge on that phone:
  the `tak_native_v1` codecs copied with their fixtures, delivery state, the
  propagation node (`OpenDecisions.md` 5), and the
  `tools/tak_partition_check.py` check after any change to the carrier.
- **BLE to a board, and BLE phone-to-phone**, with the T7 gates.
- **RNode over USB.**
- **The Columba-bound mode**: retire it, or keep it as a fallback (section 9).

## 9. Decisions for the operator

1. **Stack.** Python, as you stated, is recommended. Kotlin is the alternative,
   blocked today by talk hosting.
2. **Python layout.** Columba's pip build, or vendored pure Python with RNS's
   internal crypto. Decided by the phase 0 probe; you confirm the trade (CPU on
   key-up).
3. **Columba's backend.** Copy Columba's Python backend (MPL, hardened), or write
   a thin bridge for the plugin.
4. **LXST-kt.** (A) our own talk audio, recommended; (B) ask the authors for
   written permission; (C) counsel on the MPL-era versions. With (A) only, the
   plugin has no one-to-one calls.
5. **One owner per phone.** The plugin never hosts or joins a shared instance,
   and stands down Auto, BLE and chat when Columba is active. Confirm this, or
   choose otherwise.
6. **Identity.** The plugin has its own identity. Where the encrypted export
   lives, and whether Columba's identity may ever be *moved* into the plugin.
7. **Publishing Columba code** in this public repository (MPL allows it; privacy
   is the question).
8. **This repository's licence** (`OpenDecisions.md` 3), and the Reticulum
   License's harm clause for a public ATAK plugin (counsel).
9. **`minSdk` 24**, which drops Android 5 and 6.
10. **Keep the mesh running when ATAK closes**, or stop with it.
11. **Groups: which hub.** One hub for all of a phone's groups, or a link per
    hub.
12. **Groups: removal.** Accept that removal is advisory on our firmware hub, or
    require rrcd-style hubs (invite-only) for groups, or schedule room access
    control on the firmware hub.
13. **Groups: history.** Runtime groups as live-only rooms, or mapped onto
    provisioned, bridged rooms so absent members get the lines.
14. **Groups: long lines.** Split into marked parts, or refuse.
15. **Groups: the trust mark.** The first line and the list badge only, or also a
    suffix on the group's name.
16. **Groups: the command post.** Should its client join group rooms?
17. **Plan documents.** The firmware repository's `TAKDeliveryPlan.md` and
    `TalkChannels.md` say the ATAK talk panel is "not a second voice stack", and
    this plugin's `Roadmap.md` puts team rooms after Eridanus in Columba. Both
    change if this direction is accepted.
