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

## Order of work

1. **Decisions** (`OpenDecisions.md`): how the plugin reads the phone node's
   state -- **decided**: a bound service in Columba behind a caller allow-list;
   the SDK and ATAK versions -- **decided**: the bench on SDK 5.5.1.8,
   releases through the pipeline at the phones' version (`Setup.md`); the
   licence, still open.
2. **Mesh telemetry, in the firmware repository** (PR F's first half, already
   planned there): the board-side report and its codec, a gateway to MQTT, and
   the backend. The plugin's mesh-health view reads the same data.
3. **Scaffold**: the plugin project against the chosen SDK, building and
   loading into ATAK with an empty tool pane. Signed, sideloaded.
4. **Delivery state**: did it arrive, for messages, then files -- the most
   asked question, and the one with the least ambiguity.
5. **Queues and reachability**: who it is queued for, hops, carrier.
6. **Cost before fetching**: file offers with the route's measured rate.
7. **Propagation status** and **mesh health**.
8. **Outdoor Test 1** with the plugin installed.

Each step is its own pull request, tested on the bench phones before it
merges.
