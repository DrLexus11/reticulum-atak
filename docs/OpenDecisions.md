# Open decisions

Each of these shapes code that would be expensive to change. They are decided
here, in writing, before that code exists.

## 1. How the plugin reads the phone node's state -- decided 2026-10-02

**A bound Android service exported by Columba, gated by a caller allow-list.**

Columba declares a "Reticulum state" service with an AIDL interface: calls such
as delivery state for an item, the outbound queue per recipient, the path to a
destination (known, hops, next-hop carrier), propagation-node holdings, and a
subscription for changes, so the plugin is told rather than polling. Typed,
versioned (the plugin checks the interface version on connect), live. Columba
owns the interface; this plugin is a client.

**Why not a signature permission.** The first proposal locked the service to
apps signed with our key. That cannot work for an ATAK plugin: ATAK loads a
plugin's code into ATAK's own process, so the binding arrives from ATAK, signed
by TAK, not by us -- a signature lock would refuse our own plugin, and a plugin
cannot add permissions to ATAK's manifest.

**The gate instead:** on every bind, Columba checks the calling package and its
signing certificate against a short allow-list -- ATAK-CIV's package
(`com.atakmap.app.civ`) with TAK's certificate digest, and deliberately added
entries such as a development build of ATAK. Anything else gets nothing.

**Read-only.** The service exposes state and subscriptions only. Sending,
deleting and identity operations stay inside Columba.

**Accepted cost:** every plugin loaded into an allowed ATAK can reach the
service, not only this one -- the price of running in ATAK's process. Acceptable
because the data is read-only and a phone running ATAK already trusts the
plugins it loaded.

Alternatives considered: a local socket (the CoT endpoint or a second one) has
the same caller problem with weaker means of checking the caller; a content
provider suits lists but not live updates. The Columba side lands as its own
pull request in Columba when the plugin work starts.

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
