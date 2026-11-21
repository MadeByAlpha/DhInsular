# Insular Architecture

Insular is a fork of [Island](https://github.com/oasisfeng/island). The app itself becomes the Device Policy
Controller (DPC) of an Android Work Profile, so no external MDM is required.

This document records the **current** module structure, how the modules relate, the **decisions** taken for the
consolidation, the **target** structure, and the migration plan. Findings come from the source tree (Gradle files,
manifests, a class-level cross-reference scan and targeted greps), not from assumptions.

## 1. Core idea

1. The app becomes **Profile Owner** (or Device Owner) of a managed profile and drives `DevicePolicyManager` directly
   (hide, suspend, restrictions, cross-profile intent filters).
2. Android blocks direct calls between the owner user and the managed profile, so the app uses **Shuttle**: a lambda
   (`Context.() -> R`) is serialized as a `Closure`, sent through a cross-profile `ContentProvider` call, and executed
   in the other profile.
3. Today all modules share one UID (`sharedUserId="com.oasisfeng.island"`) and are merged into one APK by `assembly`.
   Modules never import each other. They only meet through contracts that live in `shared`.

## 2. Decisions

| # | Decision | Source |
|---|---|---|
| D1 | Minimize modules; each module has one clear responsibility | Goal |
| D2 | Merge criterion is **code complexity**: merge only if the result is not harder to understand, otherwise keep separate | Maintainer |
| D3 | `sideplay` (Google Play workaround) is merged back. Google Play distribution is dropped | Maintainer |
| D4 | The `engine`-only and `mobile`-only build flavors are dropped | Maintainer |
| D5 | The UI is a **frontend** that only controls the DPC; all UI will move to **Compose** after the consolidation | Maintainer |
| D6 | `engine` must not need Compose or any AndroidX UI library | Maintainer |
| D7 | Remove all Firebase and GMS SDK remnants | Maintainer |
| D8 | Gradle is the single source of the app version | Maintainer |
| D9 | `assembly` is merged into another module | Maintainer |
| D10 | `libs/deagle` is an external library and is **not modified** | Maintainer |

## 3. Current structure

### 3.1 Modules

| Module | Kind | Files | Responsibility |
|---|---|---|---|
| `shared` | library | 77 | Common base: `DevicePolicies`, `Users`, Shuttle, `Hacks`, `AppSettings`, API contract, analytics |
| `engine` | library | 42 | DPC core: `IslandDeviceAdminReceiver`, provisioning, public intent API (`ApiActivity`/`ApiReceiver`), `IslandPersistentService`, `DummyHomeActivity` |
| `mobile` | library | 133 | UI and user features: main app list, setup wizard, app control (freeze/hide/clone), shortcuts, settings, Shizuku/root/ADB paths |
| `installer` | library | 13 | Installs APKs into the profile (`AppInstallerActivity`), installation notifications, `AppSettingsHelperService` |
| `watcher` | library | 8 | Watches profile state and app removal (`IslandWatcher`, `IslandAppWatcher`), notifications |
| `open` | library | 18 | Open API: `SystemServiceBridge` (own `:api` process), `DelegationManager`, `DelegatedScopeAuthorization`, binder proxies |
| `sideplay` | library | 0 source | Manifest only. Declares `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `INTERACT_ACROSS_PROFILES`, `REQUEST_INSTALL_PACKAGES` |
| `assembly` | application | 0 source | Build entry point. Product flavors pick which modules are packed |
| `libs/deagle` | submodule | 74 | `Hack` reflection library, user-handle helpers, UI helpers (consumed through `shared`) |
| `libs/setup-wizard` | submodule | n/a | AOSP setup wizard library (consumed by `mobile` only) |

`sideplay` was extracted upstream in
[oasisfeng/island@2df8543](https://github.com/oasisfeng/island/commit/2df8543) so the permissions disallowed on
Google Play could ship as a separate APK. Insular does not ship on Google Play, so the extraction has no purpose here.

### 3.2 Build variants (`assembly/build.gradle`)

- `packaging`: `complete` (all modules), `engine` (engine only, same application ID, keeps profile ownership when switching builds), `mobile` (mobile only, `.mobile` suffix).
- `distribution`: `normal`, `googleplay`, `fdroid`. The last two add the `.fdroid` suffix.
- `fastlane/Fastfile` uses only `completeFdroid` and `completeGooglePlay`.
- `assembly/src/googleplay/AndroidManifest.xml` strips the three sideplay permissions with `tools:node="remove"`.

### 3.3 Compile-time relations: a pure star

```
engine ───┐
mobile ───┤
installer ┼──▶ shared ──▶ deagle
watcher ──┤
open ─────┘
```

A class-level scan found **zero** direct references between `engine`, `mobile`, `installer`, `watcher` and `open`.
References to `shared` types: engine 45, mobile 104, installer 25, open 10, watcher 10.
`mobile` additionally depends on `deagle` and `setup-wizard-lib` directly.

### 3.4 Runtime relations: five contracts, all defined in `shared`

| Contract | Defined in | Producer | Consumer | Purpose |
|---|---|---|---|---|
| `Modules.MODULE_ENGINE` (`com.oasisfeng.island.fdroid`) | `util/Modules` | n/a | `DevicePolicies`, `DeviceAdmins`, `MainActivity` | Hard-coded package name of the DPC |
| `CATEGORY_PARENT_PROFILE` / `CATEGORY_MANAGED_PROFILE` | `engine/CrossProfile` | `mobile` (`MainActivity`) | `engine` (`IslandProvisioning`, via `Modules.getMainLaunchActivity`) | Engine launches the UI without knowing it |
| `PersistentService.SERVICE_INTERFACE` | `PersistentService` | `mobile` (`ShortcutSyncService`), `installer` (`AppSettingsHelperService`) | `engine` (`IslandPersistentService`), `IslandSettings` | Plug-in style long-running services bound by `engine`, toggled by settings |
| `Api` action constants (`...action.FREEZE` etc.) | `api/Api` | external apps | `engine` (`ApiActivity`, `ApiReceiver`, `ApiDispatcher`) | Public intent API, guarded by `FREEZE_PACKAGE`, `LAUNCH_PACKAGE`, `SUSPEND_PACKAGE` |
| `Shuttle` / `ShuttleProvider` | `shuttle/*` + 4 AIDL | `shared` | `mobile` (8 files), `installer` (2), `watcher`, `open`, `shared`; `engine` only calls `ShuttleProvider.initialize` | Cross-profile RPC |

Other links:

- `installer` is entered from `mobile` (`IslandAppClones`) through an intent carrying `InstallerExtras` (type in `shared`) and `addCategory(packageName)`.
- `mobile` starts provisioning only through the system `ACTION_PROVISION_MANAGED_PROFILE/DEVICE` intents. The system then starts `engine`'s `IslandProvisioning`, which launches the UI back.
- `PersistentService` binding is limited to services of the same UID (`serviceInfo.applicationInfo.uid == myUid`).

### 3.5 Typical flows

```
Setup      SetupActivity (mobile) ─ ACTION_PROVISION_MANAGED_PROFILE ─▶ system
           ─▶ IslandProvisioning (engine): prune apps, cross-profile filters, ShuttleProvider.initialize
           ─▶ launches MainActivity (mobile) through CATEGORY_PARENT_PROFILE
Control    AppListFragment ─▶ IslandAppControl (mobile) ─▶ Shuttle ─▶ profile process
           ─▶ DevicePolicies.setApplicationHidden / setPackagesSuspended
External   3rd-party app ─ FREEZE/LAUNCH intent ─▶ ApiActivity/ApiReceiver (engine) ─▶ IslandManager
Delegation 3rd-party app ─ BIND_SYSTEM_SERVICE ─▶ SystemServiceBridge (open, :api process)
```

## 4. Current problems

1. **`shared` carries module-specific code.** Measured by which modules reference each file:

   | File | Real users | Belongs to |
   |---|---|---|
   | `provisioning/SystemAppsManager` | engine, mobile | engine (mobile also uses it) |
   | `installer/InstallerExtras` | installer, mobile | installer (moves to `mobile`) |
   | `analyzer/ApkAnalyzer` | installer | installer |
   | `shuttle/ActivityShuttle` | installer | installer |
   | `util/RomVariants` | installer | installer |
   | `home/HomeRole` | watcher | stays with the engine (watcher moves there) |
   | `IslandNameManager` | mobile, watcher | `mobile` |
   | `util/AppInfo`, `data/helper/ApplicationInfoExtensions` | mobile | `mobile` |
   | `util/ModuleContext` | installer, mobile | `mobile` |
   | `shuttle/ContextShuttle` | mobile | `mobile` |
   | `engine/IslandManager` | engine, installer, mobile | stays in the engine |

   Files with **no** user outside `shared`: `ShortcutIcons`, `UserPrefixedLog`, `MethodInvocation` (Java copy of the AIDL type), `AppSettingsProvider` (manifest-registered).
2. **Package names do not match modules.** `shared` holds `...island.engine.*`, `...island.installer.*` and `...island.provisioning.*`; `mobile` holds `...island.engine.ClonedHiddenSystemApps`; the instrumented test is in `org.securesystem.insular`.
3. **`MODULE_ENGINE` is a constant** (`com.oasisfeng.island.fdroid`) while the real application ID depends on the flavor. With the `normal` flavor (no suffix) the constant is wrong.
4. **String-based coupling the compiler cannot check:**
   - `MainActivity.onCreateInProfile()` finds engine's receiver with `name.startsWith("com.oasisfeng.island.provision")`; `ManualProvisioningReceiver` carries a comment that its package must keep that prefix.
   - `DeviceAdmins` falls back to the literal `"com.oasisfeng.island.IslandDeviceAdminReceiver"`.
   - `ApiActivityTest` (`engine/src/androidTest`) hard-codes `setPackage("com.oasisfeng.island.fdroid")`.
5. **Resources are reached across modules** through `com.oasisfeng.island.shared.R` (about 13 places, e.g. `mainland_name`, `accent`).
6. **Duplicate Java/Kotlin pairs** in `shared`: `NotificationIds` and `Hacks` exist as both `.java` and `.kt`; `MethodInvocation` exists as both AIDL and Java.
7. **Version drift.** `assembly/build.gradle` sets `versionName '5.1.2-a3fe6196'` / `versionCode 51507`; `assembly/src/main/AndroidManifest.xml` sets `6.4.2` / `64200`. The Gradle values take precedence in the build, and `.gitlab-ci.yml` points at both files as version locations.
8. **`sideplay` is Play-only plumbing** (section 3.1). Its manifest is `hasCode="false"`, labelled `Island [Extension Pack]`.
9. **No frontend/backend boundary.** `mobile` is meant to be the UI, but 22 of its source files call DPC-level APIs directly (`DevicePolicies`, `DevicePolicyManager`, `IslandManager`, `Hacks`). Calls counted in `mobile`: `policies.invoke` x15, `policies.execute` x10, `IslandManager.launchApp` x4, `setApplicationHidden` x5, plus single calls such as `setPackagesSuspended`, `setPermissionGrantState`, `setCrossProfilePackages`, `setUserRestriction`, `setPersistentPreferredActivity`, `addCrossProfileWidgetProvider` and support-message setters. Heaviest files:

   | File (under `mobile`) | Lines touching DPC APIs |
   |---|---|
   | `setup/SetupViewModel`, `setup/IslandSetup` | 21 each |
   | `controller/IslandAppControl` | 14 |
   | `controller/IslandAppClones` | 12 |
   | `settings/IslandSettingsActivity`, `settings/AppOpsPermissionsUnlock` | 11 each |
   | `adb/AdbSecure` | 10 |
   | `MainActivity` | 9 |
   | `settings/GeneralPreferenceFragment`, `adb/ProfileRestrictionsSync` | 6 each |

   Shuttle makes this possible: the UI passes a lambda that runs inside the profile, so DPC logic lives in UI files.
10. **Firebase and analytics are already dead code.** No Firebase or GMS SDK is on any classpath. What remains is scaffolding:
    - `shared/firebase/FirebaseWrapper`, `shared/.../NotificationManagerExtender` (used only by the wrapper) and the `condom` dependency (used only by the wrapper). `Config.kt` imports `FirebaseWrapper` but never uses it.
    - `AnalyticsImpl.reportEvent` and `setProperty` are empty; every `CrashReport` method except `initCrashHandler` is empty, and `initCrashHandler` installs a handler that only re-delegates.
    - Build leftovers: `assembly/google-services.json`, `CRASHLYTICS_ENABLED` build-config fields and the `firebase_crashlytics_collection_enabled` meta-data in `shared`, `firebasePerformanceInstrumentationEnabled` in `gradle.properties`, a GMS `-dontwarn` in `assembly/proguard-rules.pro`, `analytics_tracker.xml` excluded in `shared/build.gradle`.
    - At least 68 call sites still go through the `Analytics` abstraction: `event(...)` 39, `logAndReport` 19, `report` 8, `trace` and `setProperty` 2. Only `logAndReport` (a `Log.e`) and `Analytics.log` (a `Log.i`) have any effect.
    - **Not removable:** the GMS package names in `SystemAppsManager` and `WellKnownPackages`. They are not an SDK dependency. They decide which system apps stay inside the work profile (the comment there explains that disabling GMS in the profile kills the owner user's GMS and causes ANRs).
11. **Adjacent issues in `Config.kt` (reported only).** `Config.get()` reads the hard-coded `config` map and ignores each entry's `default`. As a result `URL_PLAY_ALPHA` returns an empty string (its key is not in the map), `URL_SETUP_MANAGED_MAINLAND` returns the map's `#manual-setup-for-island-in-god-mode` anchor instead of its own `#activate-managed-mainland` default, and the `"hhttps://..."` typo in `URL_SETUP_TROUBLESHOOTING`'s default is never used. `IS_REMOTE`, `isRemote()` and `Analytics.Property.RemoteConfigAvailable` are Firebase Remote Config leftovers. `SettingsActivity` still branches on `GooglePlayStore.PACKAGE_NAME` to show the Play alpha group link.

## 5. Target structure

### 5.1 Principles

1. **Frontend/backend split (D5).** `engine` is the DPC backend: the only place that calls `DevicePolicyManager`, `AppOps`, `PackageInstaller` and cross-profile state, exposed through a small typed facade. `mobile` is a pure frontend: it renders state, collects user intent and calls the facade. The dependency is `mobile → engine`, never the reverse. The engine reaches the UI only through the intent contract (`CATEGORY_PARENT_PROFILE`, `CATEGORY_MANAGED_PROFILE`), and the UI contributes background services only through the `PersistentService` action.
2. **Complexity decides merges (D2).** Each decision in 5.2 is backed by measurement.
3. **`engine` is Compose-free (D6).** See 5.3 for the verified inventory and the rule that keeps it so.
4. The `engine`/`mobile` flavors were the only reason `mobile` could not import `engine`. With D4 that constraint is gone.

### 5.2 Merge decisions

Resource-name collisions were checked for every candidate pair (strings, colors, dimens, styles, layouts, drawables, ...). There are **none**, so no merge needs resource renaming.

| Module | Decision | Evidence |
|---|---|---|
| `sideplay` | Delete | Manifest only, Google Play plumbing. Only `UPDATE_PACKAGES_WITHOUT_USER_ACTION` is new to the app, and its sole user is `AppInstallerActivity`, so the permission moves with the installer |
| `shared` → `engine` | Merge | All other modules depend on it and it depends on none of them, so it is already the base of the engine. Its extra dependencies (`okhttp`, `apk-parser`) are used only by `ApkAnalyzer`, which only the installer uses, so they move to `mobile` with the installer. Remaining work is mechanical: `shared.R` is referenced in about 13 places and `shared.BuildConfig` in 2 |
| `watcher` → `engine` | Merge | 2 classes, 4 resource files, depends only on `shared`, no UI |
| `open` → `engine`, as package `open` | Merge | Depends only on `shared`, 4 resource files, no per-module ProGuard rules (the only ProGuard file is `assembly`'s, 8 effective lines). Process isolation (`android:process=":api"`) is a manifest attribute, not a module property, so merging changes nothing at runtime. Neutral on complexity, so the lower module count wins |
| `installer` → `mobile`, whole module | Merge | It holds the only real View UI among the engine candidates, and that UI cannot be split off cheaply. See 5.3 |
| `assembly` → `mobile` | Merge, `mobile` becomes the application module | See 5.4 |

### 5.3 `engine` is UI-free (verified inventory)

Scope: `engine`, `shared`, `watcher`, `open` and `installer`, i.e. every module that was a candidate for the engine.

| Check | Result |
|---|---|
| AndroidX UI or Compose libraries in their `build.gradle` files | **None** (only `core-ktx`, coroutines, Kotlin stdlib). No `appcompat`, `material`, `fragment`, `recyclerview`, `cardview`, data binding |
| Layout, menu, theme or style resources | **1 layout** (`installer/res/layout/dialog_checkbox.xml`, a single `CheckBox`). No menus, styles or themes. The rest are strings, colors, flags, config arrays and two vector icons used by notifications |
| Activities | 7: `ApiActivity`, `DummyHomeActivity`, `IslandProvisioning.CompletionActivity`, `ShuttleCarrierActivity`, `ShuttleProvider.ReceiverActivity`, `AppInstallerActivity`, `AppInfoForwarderActivity`. All use framework `Translucent.NoTitleBar` or `NoDisplay` themes |
| Activities that draw anything | **2**: `AppInstallerActivity` and `CompletionActivity` |
| Toasts | About 15 call sites in 7 files. System UI, unaffected by Compose |
| Notifications | 9 files. System UI, unaffected by Compose |

Exactly **three UI sites** exist, all dialogs built with deagle's `Dialogs`:

| # | Site | UI | Size |
|---|---|---|---|
| 1 | `AppInstallerActivity.prepare()`, line ~136 | Alert "no installer available for cloning" with a "learn more" button (`WebContent.view`) | 4 lines |
| 2 | `AppInstallerActivity.prepare()`, line ~222 | Install confirmation alert with an "always allow" checkbox (`dialog_checkbox.xml`) | 8 lines |
| 3 | `IslandProvisioning.CompletionActivity.onCreate()` | Indeterminate, non-cancelable progress dialog while `proceed()` runs | 3 lines |

#### Handling the three sites

| Site | Options | Choice |
|---|---|---|
| 1 and 2 (`AppInstallerActivity`) | (a) Move the whole `installer` module to `mobile`. (b) Keep the activity in the engine and request the decision from a `mobile` activity through an intent and `startActivityForResult`. (c) Move only `AppInstallerActivity` to `mobile`, keep the other 5 files in the engine | **(a)** |
| 3 (`CompletionActivity`) | (a) Move it to `mobile` and make `IslandProvisioning.proceed` public as part of the profile-lifecycle facade; the `ACTION_PROVISIONING_SUCCESSFUL` intent filter moves to the `mobile` manifest. (b) Drop the dialog and rely on a notification | **(a)**. (b) changes behavior: this path runs `proceed` inside the activity and does not start the `IslandProvisioning` foreground service, so the dialog is the only progress indicator |

Why (a) for the installer, measured against D2:

- `AppInstallerActivity` is 415 lines (47% of the installer's 880) and mixes validation, caller qualification, session handling and both dialogs in one `prepare()` flow. Option (b) restructures `prepare()` into stages that cross an activity boundary. Option (c) leaves most of the logic in the frontend anyway and splits a module that is cohesive today.
- The other 5 files have no UI beyond a toast or a notification and are tied to the activity: `AppInstallerStatusReceiver`, `AppInstallationNotifier` and `AppInstallerUtils` are referenced by `AppInstallerActivity`, and `AppInfoForwarderActivity` by `AppInstallationNotifier`.
- Nothing else references `AppInstallerActivity` (only its manifest entry and an intent from `mobile`), so moving the module changes no call sites.
- Cost: `mobile` gains the installer's engine-level calls (`DevicePolicies` 5, `AppOpsCompat` 3, `ApkAnalyzer` 2, `IslandManager` 2, `Hacks` 1 and similar). They are handled by the facade work (5.6) like the DPC calls already in `mobile`.

#### Other findings

- **`deagle` is left untouched (D10), so Compose-freedom of the engine is enforced by rule, not by the compiler.** `shared` exposes `deagle` with `api`, so the engine's classpath still contains deagle's UI helpers and its `appcompat`, `material`, `recyclerview` and data-binding setup. Rule for the engine after the three sites move out:
  - It may use `com.oasisfeng.android.widget.Toasts` and `com.oasisfeng.android.ui.IconResizer`.
  - It must not import anything else from `com.oasisfeng.android.ui.*` (`Dialogs`, `WebContent`, `Snackbars`, `AboutActivity`), nor any `androidx` UI or Compose package.
  - The rule can be checked with a plain `grep` in CI over `engine/src`.
- **UI-facing resources live in `shared`:** colors `primary` and `accent`, strings `mainland_name`, `action_learn_more`, `device_admin_support_message_*`, icons `ic_landscape_black_24dp` and `ic_settings_applications_white_24dp`. The colors and icons are also used as notification tints and icons by the engine, so they stay in the engine. This is a layering smell, not a Compose blocker: Compose reads them through `stringResource` and `colorResource`.

### 5.4 `assembly` merged into `mobile`

`assembly` has no source. It carries: signing and release config, `resourceConfigurations`, version fields, an 8-line ProGuard file, `app_name` strings (en, zh), `backup.xml`, a manifest (`sharedUserId`, plus a duplicate `MainActivity` declaration that exists only "to ensure Run/Debug in Android Studio"), and `google-services.json`.

- `mobile` switches from `com.android.library` to `com.android.application` and takes over signing, minify, version, `resourceConfigurations`, the ProGuard file, the `app_name` strings and `backup.xml`.
- **No R-class churn.** AGP 8 separates `namespace` from `applicationId`. `mobile` keeps `namespace 'com.oasisfeng.island.mobile'`, so the 25 `import ...mobile.R` lines stay, and `applicationId` becomes `com.oasisfeng.island.fdroid`.
- `mobile` already has `MainActivity`, the launcher icons, `dataBinding` and compose. The `dataBinding true` in `assembly` becomes redundant. `google-services.json` and the duplicate `MainActivity` entry are deleted.
- `missingDimensionStrategy` and the `setup-wizard-lib` configuration dependencies work unchanged in an application module.
- **Keep `android:sharedUserId="com.oasisfeng.island"`.** A single APK does not need it, but Android refuses to update an installed app whose `sharedUserId` changed or was removed, so dropping it would force every existing user to reinstall and lose the profile ownership. Code that enumerates packages of the same UID (`Modules.broadcast`, `bindPersistentServices`) keeps working and can be simplified later without touching the manifest.
- CI and Fastlane references to `assembly` must be updated: `.gitlab-ci.yml` (`VERSION_BUILD_GRADLE_LOCATION`, `VERSION_ANDROID_MANIFEST_LOCATION`, artifact path `assembly/build/outputs`) and the `.aab` path in `fastlane/Fastfile`. The version-setting job comes from an external template (`android-set-version-ci.yml`) that was not read, so whether it tolerates the manifest location being absent is **unverified**.

### 5.5 Resulting structure

```
mobile    application   frontend: all UI, installer, CompletionActivity; signing, version, ProGuard, app resources
 └─ engine   library       backend, Compose-free: shared + watcher + open
     └─ deagle            external, untouched
(mobile also uses libs/setup-wizard)
```

Two Gradle modules plus two submodules, down from eight modules.

`engine` layout by package (the package must say what the code is):

```
engine/
  util/ shuttle/ settings/ ...     former shared: DevicePolicies, Users, Shuttle, AppSettings
  provisioning/ api/ home/         former engine: provisioning, public intent API
  watcher/                         former watcher
  open/                            former open (SystemServiceBridge, delegation)
```

`mobile` gains `installer/` (former module) and `CompletionActivity`, next to its existing UI packages.

### 5.6 Engine facade

Proposed grouping of the DPC calls found in `mobile`. The grouping is by file name and call usage, so each file needs checking before it is moved.

| Facade | Replaces direct calls in | Operations |
|---|---|---|
| App control | `IslandAppControl`, `IslandAppClones`, `ClonedHiddenSystemApps`, `IslandAppShortcut`, `FeatureAction`, `AppInstallerActivity` | hide/unhide, suspend, launch, enable system app, clone, freeze |
| Profile lifecycle | `SetupViewModel`, `IslandSetup`, `IslandSettingsActivity`, `MainActivity`, `CompletionActivity` | create, re-provision, destroy, activation checks, device owner vs profile owner |
| Security and permissions | `AdbSecure`, `ProfileRestrictionsSync`, `AppOpsPermissionsUnlock`, `GeneralPreferenceFragment`, `OpsManager` | user restrictions, app-ops, permission grants, cross-profile packages and widgets, support messages |
| State queries | `IslandAppListProvider`, `LiveProfileState`, `LiveUserRestriction`, `IslandAppInfo` | profile list, owner status, hidden/suspended state, observed as `LiveData` |

Remaining `mobile` files (`model/*`, `ui/*`, `widget/*`, `featured/*`, `guide/*`, `console/*`, `common/app/*`) are UI-only apart from a few single-line hits.

## 6. Migration plan

Each step ends with a full build. Steps 3 and later also run the existing instrumented tests where a device is available.

| # | Change | Risk |
|---|---|---|
| 1 | Delete `sideplay`, the `googleplay` and `normal` flavors, the `engine` and `mobile` packaging flavors, and the Play lanes in `fastlane/Fastfile`. Move `UPDATE_PACKAGES_WITHOUT_USER_ACTION` into the manifest of the module that will hold `installer` (`mobile`) | Low. Diff the merged manifest before and after |
| 2 | Remove the Firebase scaffolding (section 4, item 10): `FirebaseWrapper`, `NotificationManagerExtender`, `condom`, `CRASHLYTICS_ENABLED`, `firebase_*` meta-data, `google-services.json`, Firebase and GMS lines in `gradle.properties` and ProGuard, the `analytics_tracker.xml` exclusion, the unused import in `Config.kt`. Keep the GMS package names in `SystemAppsManager` | Low |
| 3 | Remove the `Analytics` abstraction: delete the no-op `event`, `report`, `trace`, `setProperty` calls, turn `logAndReport` into a plain `Log.e`, delete `Analytics`, `AnalyticsImpl`, `CrashReport` | Medium for size: at least 68 call sites in `mobile` (41), `shared` (21), `engine` (13), `installer` (3), `open` (4). Mechanical, one commit |
| 4 | Fold `shared`, `watcher`, `open` into `engine` and `installer` into `mobile`, one module per commit. Single-user classes go to their owner: `ApkAnalyzer`, `ActivityShuttle`, `RomVariants`, `InstallerExtras`, `okhttp`, `apk-parser` to `mobile` with the installer; `AppInfo`, `ContextShuttle`, `IslandNameManager`, `ModuleContext` to `mobile`; the rest stays in `engine` | Low to medium. `open` needs a manifest and ProGuard check |
| 5 | Convert `mobile` to the application module and delete `assembly` (5.4). Update CI and Fastlane paths | Medium |
| 6 | Gradle becomes the only version source (D8): remove `android:versionCode` and `versionName` from the manifest, point the CI version variable at `mobile/build.gradle` | Low once the value is chosen (open item 2) |
| 7 | Replace string couplings (section 4, item 4) with compile-checked references: `MainActivity` and `DeviceAdmins` reference the receiver class directly, and `MODULE_ENGINE` becomes `context.packageName` or a build constant. Rename misleading packages, drop the duplicated Java/Kotlin pairs | Medium |
| 8 | Move `CompletionActivity` to `mobile` (5.3) | Low to medium. The intent filter and `directBootAware` move with it; behavior on a real provisioning run is unverified |
| 9 | Introduce the engine facade (5.6) and move DPC calls out of `mobile`, one facade at a time: state queries, app control, security and permissions, profile lifecycle (lowest to highest risk) | High. This is the real refactor |
| 10 | Add the Compose-free check for `engine/src` (5.3) to CI | Low |

After step 10, the Compose migration of `mobile` can start without touching the engine.

## 7. Open items

1. **Analytics removal scope.** Step 3 is large but mechanical and every touched call is a no-op or a log line. Recommended: keep it in this consolidation as its own commit.
2. **Version value.** `assembly/build.gradle` has `5.1.2-a3fe6196` / `51507` (what AGP builds today); the manifest has `6.4.2` / `64200` (upstream's release number). `versionCode` must never go down for installed users, so the value to keep must be chosen against the F-Droid publishing history.
3. **CI template compatibility.** `android-set-version-ci.yml` is external and was not read (5.4).
4. **Play-only code in `SettingsActivity`** (`GooglePlayStore.PACKAGE_NAME`, alpha-group link): remove it together with step 1, or leave it. It becomes dead once Play distribution is dropped.
5. **`CompletionActivity`**: step 8 keeps the progress dialog by moving the activity. Dropping the dialog is the alternative if the behavior change is acceptable.
