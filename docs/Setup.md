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
ATAK is uninstalled. That deletes what ATAK keeps in its private storage --
preferences, callsign, network and server settings, certificates. What it keeps
in shared storage (`/sdcard/atak`: data packages, imported maps) survives and is
found again by the developer ATAK, as it was on the bench phone on 2026-10-02.
Export the settings that are needed first.

A plugin signed with the SDK's development key loads only in the developer
ATAK. The store ATAK needs a build from TAK.gov's third-party pipeline
(`OpenDecisions.md`, 2).

**Decided 2026-10-02: the bench device is the spare phone (a Nexus 6P,
Android 8.1, API 27).** The developer ATAK needs API 21 or later. Its store
ATAK was uninstalled and replaced by the SDK's `atak.apk` (5.5.1.8) on
2026-10-02; the scaffold loaded in it the same day.
The other phone and the deck's ATAK stay on the store build.

## Deployment does not need the developer ATAK

Responders' phones keep the store ATAK. A release of the plugin goes through
TAK.gov's third-party pipeline, and the build it returns is signed so that the
store ATAK loads it: install the plugin, nothing else changes. The pipeline's
turnaround sets the release cadence, not the bench's.

## Versions: the bench builds against 5.5.1.8, the pipeline against the phones

**Decided 2026-10-02.** 5.5.1.8 is the newest SDK published for download; the
phones run newer store builds (5.6 and 5.8 at the time of writing). A plugin
names its target in `ATAK_VERSION` (`build.gradle`), which becomes the
manifest's `plugin-api` value, `com.atakmap.app@<version>.CIV`.

- **Bench:** `ATAK_VERSION` 5.5.x, built against the local SDK, loaded into the
  SDK's developer ATAK 5.5.1.8 on the bench phone. A matched pair.
- **Release:** the third-party pipeline does not use our SDK. Its
  `atak-gradle-takdev` plugin fetches the SDK for the declared `ATAK_VERSION`
  from TAK's Maven repository, which holds versions the download page does
  not. A release sets `ATAK_VERSION` to the ATAK on the phones it is for.
- **What we cannot check locally:** that repository
  (`artifacts.tak.gov`) is reserved for US government personnel, so the
  pipeline's recommended pre-submission build with those credentials is not
  available to us. Our check is a clean `assembleCivRelease` against the local
  SDK; a difference between 5.5 and the target version's API shows up only as a
  failed pipeline build. The plugin keeps to the surfaces that change least
  (tool pane, map items, chat lines) to make that rare.
- **Not done:** relabelling a 5.5 build as a later version. Nothing checks the
  API underneath, and the store ATAK would refuse its signature anyway.

## A pipeline release, as first done (2026-10-02)

The scaffold, unchanged except `ATAK_VERSION = "5.8.0"`, submitted as a zip.

- **Nothing is asked at upload.** The pipeline reads the target from
  `ATAK_VERSION` in `app/build.gradle`; to target another ATAK, change that
  line and submit again. One zip, one target.
- **It builds against the real target.** The build log shows the SDK API,
  ProGuard mapping and signing material resolved from TAK's repository for
  `5.8.0` (`5.8.0-SNAPSHOT` to `5.8.1-SNAPSHOT`) -- newer than any SDK published
  for download.
- **About half an hour.** Queued, then 26 minutes, of which the Gradle build
  was 2; the rest is a Fortify scan, a dependency check and an SBOM. An email
  reports the status.
- **What comes back:** the release APK and AAB, the ProGuard mapping, the
  build log, Fortify results, a dependency-check report and an SBOM
  (CycloneDX JSON and XML). None of it goes in this repository.
- **The signature:** `CN=TAK Product Center ATAK Untrusted Plugin Release` --
  TAK's third-party key, not ours and not TAK's own plugin key.
- **Version code 1.** A zip carries no git history, so the template's
  git-derived version name is empty and the code falls back to 1. A release
  needs `takStaticVersion` (or another explicit code) so ATAK sees upgrades.
- **Loading into a store ATAK:** not yet confirmed.

## Pipeline requirements the project keeps from the first commit

From TAK.gov's source archive requirements, so a release never needs a
restructure:

- Gradle, with the wrapper and scripts at the project root; the
  `assembleCivRelease` task defined.
- Every ATAK SDK reference through `atak-gradle-takdev`. The requirements page
  says `2.+`; the 5.5.1.8 template uses `3.+`, and the project follows the
  template, which is the newer of the two.
- The submission is a zip with a single root folder, whose name becomes the
  APK name.
- ProGuard's `-repackageclasses atakplugin.PluginTemplate` replaced with this
  plugin's own name, so crash logs identify it. The template's build file
  writes it from the Gradle project name, so `rootProject.name = "ReticulumAtak"`
  in `settings.gradle` gives `atakplugin.ReticulumAtak`.
- The manifest's discovery entry:

  ```xml
  <activity android:name="com.atakmap.app.component" tools:ignore="MissingClass">
    <intent-filter android:label="@string/app_name">
      <action android:name="com.atakmap.app.component" />
    </intent-filter>
  </activity>
  ```

Pipeline-signed plugins carry a visual marker in ATAK showing they came from
the third-party service rather than TAK's own build pipeline.
