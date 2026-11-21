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

(Moved in on 2026-10-03, operator: the **Interfaces page**, with switches from
the start, as a quick diagnosis tool the outdoor test itself will want. It is
the plugin's second swipeable page -- see "Pages" below.)

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
6. **Interfaces page** (moved in): Columba's interfaces in the snapshot, and a
   switch command behind "allow ATAK control" with Columba's guard
   (`OpenDecisions.md` 1); the plugin's pager with Mesh as page 1 and
   Interfaces as page 2.
7. **Outdoor Test 1** with the plugin installed.

**After Outdoor Test 1**, each its own pull request: propagation
node selection and fallback; NomadNet pages, then feeds; delivery state and
queues; cost before fetching; team rooms over RRC once Eridanus is merged.

Each step is tested on the bench phones before it merges.

## Pages

The plugin's window grows by **swipeable pages**, the structure ATAK uses for
marker details: an androidx `ViewPager` (ATAK ships it and keeps `androidx.**`
unobfuscated, so the plugin bundles nothing) under a row of tabs, one per page
-- swipe or tap. Tabs rather than the sample's dots, because a page reachable
only by swiping is one nobody finds. TAK's own sample is `helloworld`'s
`TabViewDropDown`; the plugin uses views rather than its fragments, because a
pane has no fragment manager of its own. With one page (a Columba without the
interfaces capability), there are no tabs. Page 1 is **Mesh** (status, peers,
announce, the overlay's controls); page 2 is **Interfaces**. Later features
take further pages rather than crowding the first.

## The overlay -- next

Accepted for PR F as it is (operator, 2026-10-03), with two improvements for
later:

- **Pagination**: with more favourites than fit, pages of rows rather than a
  column that runs off the map.
- **A small Announce button** on the overlay, so announcing does not need the
  panel open. Scheduled 2026-11-21 (operator): an icon in the overlay's header,
  **long-press** to announce, so a stray tap spends no airtime (on a slow LoRa
  preset an announce costs a second or more). One line back, state first
  (`ANNOUNCED 14:02`), and greyed for a cooldown. Columba's announce command
  exists; no Columba change. See *Talk on the map* below, step 1.

## Talk on the map -- scheduled 2026-11-21

The Talk page stays as it is. Added beside it, decided by the operator on
2026-11-21:

- **The keys on the map.** A setting, off by default, puts the A and B keys on
  the map as an overlay, like the favourites: **press to talk, as on Columba's
  own talk screen; no latch mode**, because a latch is how a key stays on.
  Each key shows its channel with a step down and a step up beside it, and one
  state line, tinted by phase (`TALKING`, `LISTENING`). Drawn with ATAK's map
  widgets, which report press and release separately.
  - **To prove first, on a bench phone:** the Talk page holds a key while a
    finger slides off it; a map widget may not report that release the same
    way, and a missed release is a stuck key. Columba's maximum hold and the
    press's token are the backstop, not the design. If the widgets cannot hold
    a key safely, the overlay keys are drawn another way before they ship.
- **Changing channels.** The step buttons stay as the knob. The list button
  becomes ATAK's `TileButtonDialog`: large tiles, positions 1..12, each with
  its channel and room, the current one marked -- the radio's channel sheet.
  The same picker opens from the Talk page and from the overlay (tap the
  channel). No Columba change: it uses channel select (capability bit 4).
- **Audio where the keys are.** One button cycling Speaker, Earpiece and
  Bluetooth headset, and the talk volume through ATAK's on-map slider. Columba
  keeps these today and offers no command for them, so **a Columba capability
  comes first** (a new bit, behind "allow ATAK control", additive to
  `ColumbaInterface.md` like channel select), then the plugin's controls.
- **Volume keys as PTT**, as some voice plugins do -- **trial**: it needs an
  accessibility service, an extra permission that also weighs in TAK.gov's
  release review. Tried, and cancelled down the line if it is a hurdle.
- **ATAK Settings**, for set-once choices only: the overlay keys on or off,
  their size, which keys show, the announce cooldown. Nothing needed in the
  middle of an incident lives there.
- **Later, optional:** the talker's marker highlighted on the map, from the
  floor holder Columba already reports.

Order, each its own pull request, each tested on the bench phones before it
merges:

1. The overlay keys (press to talk) and the overlay's Announce icon
   (long-press).
2. The tile channel picker, on the Talk page and the overlay.
3. Columba's audio capability (a Columba pull request), then the audio
   controls here.
4. The volume-key PTT trial.
5. The talker on the map, if wanted.
