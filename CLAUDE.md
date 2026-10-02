# reticulum-atak

ATAK plugin for Reticulum delivery state, queues, reachability, fetch cost and
propagation status. PR F of the programme in
`~/projects/microReticulum_Firmware/docs/TAKDeliveryPlan.md`, which is the
schedule this repository runs in.

## Read this before planning work

- `docs/Roadmap.md` -- the order of work and what gates what.
- `docs/OpenDecisions.md` -- decisions that must be settled before the code
  they shape. Do not decide them by writing code.
- The firmware repository's `docs/TAKDeliveryPlan.md` (*PR F*, *The second
  plugin repo*) -- why this repository exists and what it owns.

Update the roadmap when the order changes; phasing that lives only in a
conversation is re-derived a week later.

## Sibling repositories

- `~/projects/microReticulum_Firmware` -- ESP32 mesh firmware, the deck's CoT
  bridge, and the codecs. Owns how things travel.
- `~/projects/columba` -- Android Reticulum/LXMF app: the phone's node. This
  plugin reads Reticulum state through it.
- `~/projects/urban-tak` -- ATAK plugin for navigation and building status.
  Must never depend on Reticulum; nothing Reticulum-specific goes there.
- `~/projects/microReticulum` -- the C++ Reticulum library.

## Standing constraints

- **Public repository.** No fleet secrets, node or destination hashes,
  identities, callsigns, IFAC passphrases, exercise coordinates or team
  details, in any file or commit message. Name the concepts, never the fleet.
  Test data is synthetic.
- **Push to `origin` (DrLexus11) only.** Branch for work; open a PR; merge the
  PR. Wait 5-10 minutes for Copilot's review, fix its first round, then merge.
- **Wire formats are shared contracts.** Anything this plugin decodes is
  defined in the firmware repository and pinned by its fixtures
  (`tak_native_v1.json` and successors). Copy fixtures; never redefine a format
  here.
- **Tactical lines.** Anything shown in ATAK is one short line, state first --
  `HELD ... slow path`, not a paragraph. The small screens decide.
- **Disaster-first.** Weigh re-meshing speed and a responder's attention
  against everything else. Never assume a fast path exists.
- **Interface completeness.** State is judged on every carrier the fleet has
  -- LoRa, BLE (phone-to-board and phone-to-phone), Wi-Fi/TCP/UDP, ESP-NOW,
  HaLow -- never only the one it was tested on, and never from a declared
  bitrate.
- Fleet secrets and passphrases are prompted on a terminal, never taken as
  arguments, and never stored here.
