# Open decisions

Each of these shapes code that would be expensive to change. They are decided
here, in writing, before that code exists.

## 1. How the plugin reads the phone node's state -- open, blocks everything

The plugin runs inside ATAK's process; Reticulum runs in Columba's. Options:

- **Bound Android service (AIDL) exported by Columba.** Typed, push and pull,
  permission-gated by signature or a custom permission. Needs a stable
  interface version in Columba and a client here.
- **Local socket from Columba** (the CoT endpoint, or a second one for state).
  Reuses an existing channel; state would be a new message family on it.
- **Content provider exported by Columba.** Natural for queues and history,
  awkward for live updates.

Whichever is chosen, Columba owns the interface and its version; this plugin is
a client. The choice also decides how much of Columba's Reticulum state is
exposed to other apps, so it is a security decision as much as a plumbing one.

## 2. ATAK-CIV SDK and ATAK version -- open, blocks the scaffold

The SDK must match the ATAK build on the phones (and the deck's Waydroid).
Checked 2026-10-02:

- **Source: tak.gov**, with a free account; the civilian SDK does not need a
  government one. It is no longer published on GitHub.
- **Not** the archived `deptofdefense/AndroidTacticalAssaultKit-CIV` repository
  (read-only since 2025-05-02, ATAK 4.x era -- too old), and **not** unofficial
  mirrors of newer SDKs: their provenance and licensing cannot be checked, and
  this project should not build on binaries it cannot verify.
- **Signing:** ATAK-CIV loads only plugins signed with the keystore distributed
  in the SDK -- enough for the bench. A release goes through TAK.gov's
  third-party pipeline (a source zip submitted at tak.gov/user_builds).

Waiting on: the operator's tak.gov account, and the ATAK version on the phones.
`urban-tak` needs the same; one setup should serve both.

## 3. Licence -- open

Not chosen. Constraints to weigh: this plugin reads Reticulum state but does not
link Reticulum if decision 1 keeps it in Columba's process; Reticulum's own
licence restricts use in systems designed to harm people and use as AI training
data; the programme plans to move its repositories private after PR F.

## 4. Mesh-health data source -- leaning, not settled

The firmware repository's PR F telemetry publishes per-board reports to MQTT
through a gateway. The plugin could read them from the backend over IP, or from
the mesh directly through the phone's node. Over the mesh is
disaster-first (no infrastructure needed) but costs airtime; over IP is cheap
but absent exactly when it matters. Leaning: over the mesh, rate-limited, with
IP as a bonus when present.
