# Roadmap

PR F of the programme (`microReticulum_Firmware/docs/TAKDeliveryPlan.md`). It
comes before Outdoor Test 1, which then exercises the plugin as well.

## What the plugin shows

Each of these is a question a responder asks and today cannot answer from
ATAK alone:

| Question | State it needs | Where that state lives |
| --- | --- | --- |
| Did my message or file arrive? | per-item delivery state: sent, held, delivered, failed | the phone's node (Columba) |
| Who is it queued for, and why? | outbound queue per recipient; the reason (no path, slow path, waiting for a fast one) | the phone's node; the deck bridge's status lines |
| How far away are they? | path known or not, hops, next-hop carrier | the phone's node |
| What will this cost before I spend it? | file size against the measured rate of the route | the file offer plus the route's timed parts |
| Is anything held for me? | propagation-node contents for this identity | the propagation node, through the phone's node |
| Is the mesh around me healthy? | per-board telemetry: up, restarts, peers, carriers | mesh telemetry (PR F's first half), through a gateway |

## Features -- agreed 2026-10-02

1. **Mesh panel** -- the main window. The mesh as this phone sees it: peers
   with hop count and next-hop carrier, whether the command post is reachable
   over the mesh, last heard. A locate button per peer pans the map to its
   marker (peers are ATAK contacts under their Reticulum-derived UIDs, so the
   marker is found by UID). Board health joins it once decision 4 (where
   mesh-health data comes from) is made; it is not in PR F.
2. **Messaging through GeoChat** -- a peer's message button opens ATAK's own
   GeoChat with that contact, which already travels over LXMF through
   Columba; the plugin adds no chat of its own. An **announce** button, in
   reach on the panel.
3. **Interfaces** -- Columba's configured interfaces shown, and switched from
   ATAK. Columba refuses a switch that would leave the phone without a path to
   the command post or with no interface up (`OpenDecisions.md` 1): the guard
   is in the command, not the plugin's UI.
4. **Propagation node** -- pinned to the command post, with fleet fallback
   (`OpenDecisions.md`, 5). The panel says which node is in use, in one line,
   state first.
5. **NomadNet pages** -- fetched through Columba (which already has a NomadNet
   browser and a micron renderer) and shown in a pane; later, **data feeds**: a
   versioned machine-readable page at a known path that the plugin turns into
   map items, specified and pinned by fixtures like the firmware's codecs.
6. **Team rooms over RRC** -- after Eridanus merges into Columba (see the
   firmware repository's `docs/TAKDeliveryPlan.md`): ATAK team chat carried by
   rooms hosted on the boards' RRC hubs, so a member out of range gets the
   backlog instead of losing the lines.

Still wanted from the first roadmap: **delivery state** (did it arrive, per
message and file), **queues** (who it is waiting for, and why), **cost before
fetching** (a file's size against the route's measured rate).

## Order of work

**PR F, before Outdoor Test 1:**

1. Decisions -- **made** (`OpenDecisions.md` 1, 2, 5); the interface they
   imply, `ColumbaInterface.md`, with its fixture.
2. Mesh telemetry, in the firmware repository (F1-F5).
3. Scaffold -- **done**, and the release path proven through TAK.gov's
   pipeline.
4. **Columba's bound service** (a Columba pull request): the read surface the
   panel needs, the caller allow-list, the "allow ATAK control" gate, and the
   first command, announce.
5. **Mesh panel** with locate, open-GeoChat and announce; the propagation
   node's status. Board health waits for decision 4.
6. **Outdoor Test 1** with the plugin installed.

**After Outdoor Test 1**, each its own pull request: interfaces; propagation
node selection and fallback; NomadNet pages, then feeds; delivery state and
queues; cost before fetching; team rooms over RRC once Eridanus is merged.

Each step is tested on the bench phones before it merges.
