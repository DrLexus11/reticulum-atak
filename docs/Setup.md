# Setup

## The ATAK-CIV SDK

- **Version: 5.5.1.8** (`ATAK CIV SDK 7f381e4`, versionCode 1761249075),
  downloaded from tak.gov. The official source repository for the same release
  line is `TAK-Product-Center/atak-civ` on GitHub; its `pluginsdk.zip` is only
  the test harness, so builds use the full SDK bundle below.
- **Location: `~/tak/sdk/5.5.1.8/`**, outside every repository. It is
  distributed by TAK.gov under its own licences (`license/` inside it) and is
  not committed here. A build points at it with `sdk.path` in
  `local.properties`, never by copying it into the tree.
- **Contents that matter:** `main.jar` (the plugin API), `atak.apk` (a
  *developer* ATAK for the bench), `android_keystore` (the development signing
  key), `atak-gradle-takdev.jar`, `samples/` (`plugintemplate`, `helloworld`
  and others), `ATAK_Plugin_Development_Guide.pdf`.

## The bench device -- a decision

The developer `atak.apk` is package `com.atakmap.app.civ` -- **the same package
as the store ATAK-CIV, signed with a different key**. Android will not install
one over the other: a device moves to the developer ATAK only after the store
ATAK is uninstalled, which deletes ATAK's local data there (settings, callsign,
maps, data packages, server certificates). Export what is needed first.

A plugin signed with the SDK's development key loads only in the developer
ATAK. The store ATAK needs a build from TAK.gov's third-party pipeline
(`OpenDecisions.md`, 2).

Which device becomes the bench is the operator's call (pending): a spare phone
is cleanest; otherwise one bench phone after exporting its ATAK setup, keeping
the other phone and the deck's ATAK as they are.
