# Insular Architecture

Insular is a fork of [Island](https://github.com/oasisfeng/island). The app itself becomes the Device Policy
Controller (DPC) of an Android Work Profile, so no external MDM is required.

This document records the current module structure, how the modules relate, the decisions taken for the
consolidation, the target structure, and the migration plan with its status. Findings come from the source tree
(Gradle files, manifests, a class-level cross-reference scan and targeted greps), not from assumptions.

## 1. Core idea

1. The app becomes **Profile Owner** (or Device Owner) of a managed profile and drives `DevicePolicyManager` directly
   (hide, suspend, restrictions, cross-profile intent filters).
2. Android blocks direct calls between the owner user and the managed profile, so the app uses **Shuttle**: a lambda
   (`Context.() -> R`) is serialized as a `Closure`, sent through a cross-profile `ContentProvider` call, and executed
   in the other profile.
3. All modules end up in one APK and one UID (`sharedUserId="com.oasisfeng.island"`). Library modules never import
   each other. They only meet through contracts that live in `shared`.

## 2. Decisions

| # | Decision | Status |
|---|---|---|
| D1 | Minimize modules; each module has one clear responsibility | Goal |
| D2 | Merge criterion is **code complexity**: merge only if the result is not harder to understand, otherwise keep separate | Applied in section 5.2 |
| D3 | `sideplay` (Google Play workaround) is merged back. Google Play distribution is dropped | Done |
| D4 | The `engine`-only and `mobile`-only build flavors are dropped | Done |
| D5 | The UI is a **frontend** that only controls the DPC; all UI will move to **Compose** after the consolidation | Planned |
| D6 | `engine` must not need Compose or any AndroidX UI library | Planned, see 5.3 |
| D7 | Remove Firebase, Analytics and every other part where GMS is involved, including Play-only branches | **Deferred**, to be done later |
| D8 | Gradle is the single source of the app version. The version stays at `64200` / `6.4.2` | Done |
| D9 | `assembly` is merged into the UI module, and that module is renamed `mobile` → `app` | Done |
| D10 | `libs/deagle` is an external library and is **not modified** | Applies to all steps |
| D11 | Build scripts use Kotlin DSL on the latest AGP and Gradle | Done for root and `app`, see section 3.2 |
| D12 | Build scripts first, code changes afterwards. CI and Fastlane files were removed; CI will be rewritten for GitHub Actions | In progress |

## 3. Current structure

### 3.1 Modules

| Module | Kind | Files | Responsibility |
|---|---|---|---|
| `app` | application | 133 | Former `mobile` plus `assembly`. UI and user features: main app list, setup wizard, app control (freeze/hide/clone), shortcuts, settings, Shizuku/root/ADB paths. Owns signing, version, ProGuard and app resources |
| `shared` | library | 77 | Common base: `DevicePolicies`, `Users`, Shuttle, `Hacks`, `AppSettings`, API contract, analytics |
| `engine` | library | 42 | DPC core: `IslandDeviceAdminReceiver`, provisioning, public intent API (`ApiActivity`/`ApiReceiver`), `IslandPersistentService`, `DummyHomeActivity` |
| `installer` | library | 13 | Installs APKs into the profile (`AppInstallerActivity`), installation notifications, `AppSettingsHelperService`. Also declares the permissions of the former `sideplay` |
| `watcher` | library | 8 | Watches profile state and app removal (`IslandWatcher`, `IslandAppWatcher`), notifications |
| `open` | library | 18 | Open API: `SystemServiceBridge` (own `:api` process), `DelegationManager`, `DelegatedScopeAuthorization`, binder proxies |
| `libs/deagle` | submodule | 74 | `Hack` reflection library, user-handle helpers, UI helpers (consumed through `shared`). Not modified |
| `libs/setup-wizard` | submodule | n/a | AOSP setup wizard library (consumed by `app` only). Not modified |

Removed: `assembly` (merged into `app`) and `sideplay`.
`sideplay` was extracted upstream in
[oasisfeng/island@2df8543](https://github.com/oasisfeng/island/commit/2df8543) so the permissions disallowed on
Google Play could ship as a separate APK. Insular does not ship on Google Play, so it was reverted into `installer`.

### 3.2 Build

- **Toolchain:** Gradle 9.9.2, AGP 9.5.2, Kotlin 2.4.21, JDK 17 or newer (verified with JDK 21). Compose uses the `org.jetbrains.kotlin.plugin.compose` plugin. `compileSdk 35`, `minSdk 27`, `targetSdk 34`.
- **Scripts:** `settings.gradle.kts`, `build.gradle.kts` and `app/build.gradle.kts` are Kotlin DSL. The five internal libraries are still Groovy and disappear with the module merge (5.5, change 4).
- **`app`:** `applicationId` is `com.oasisfeng.island.fdroid` (keeps `Modules.MODULE_ENGINE` valid), version `64200` / `6.4.2`, `sharedUserId` is kept. `namespace` is still `com.oasisfeng.island.mobile`, so no source or `R` import changed; renaming it is a code change for later. There are no product flavors.
- **AGP 9 modes:** `app` uses the new DSL and built-in Kotlin. The internal libraries, `deagle` and `setup-wizard-lib` still apply `kotlin-android` and the old DSL, so `gradle.properties` sets `android.builtInKotlin=false` (with `app` applying `com.android.built-in-kotlin` itself) and `android.newDsl.optOut` for those modules.
- **Signing:** release builds read `secrets.properties` at the repository root, otherwise the environment variables `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`. The keystore is `android-signing-keystore.jks` at the repository root.
- **Build:** `JAVA_HOME=<JDK 17+> ./gradlew :app:assembleDebug`.
- **Verified:** `help`, `build --dry-run`, `:app:assembleDebug`, `:app:minifyReleaseWithR8`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`. The APK reports `com.oasisfeng.island.fdroid` 6.4.2 (64200), `sharedUserId`, the merged permission set, and locales `en`, `pt-BR`, `zh`, `zh-TW`. **Not verified:** a release build (needs the keystore) and any run on a device.
- **Edits forced by the toolchain** (the only source and dependency changes so far):
  - `MainViewModel.kt`: `application` became `getApplication<Application>()`, because Kotlin 2.x does not resolve the private field of `AndroidViewModel`.
  - `androidx.activity:activity-compose` is pinned to `1.8.0` (the Compose BOM does not manage it). `androidx.test:core` is `1.5.0` (older versions fail the manifest merge for `android:exported` at `targetSdk 34`).
  - The root script sets the Java 8 source level for the external `setup-wizard-lib` (compileSdk 26), because AGP 9 defaults to a newer level. No submodule file was touched.
- **Known risk:** the opt-out properties are removed in AGP 10. The internal libraries will be gone by then, but `deagle` (Groovy, `kotlin-android`, `kotlinOptions`) and `setup-wizard-lib` are external and would stop building. The options are to update them upstream, or to build them with a build script owned by this repository.

### 3.3 Compile-time relations: a pure star

```
engine ───┐
app ──────┤
installer ┼──▶ shared ──▶ deagle
watcher ──┤
open ─────┘
```

A class-level scan found **zero** direct references between `engine`, `app`, `installer`, `watcher` and `open`.
References to `shared` types: engine 45, app 104, installer 25, open 10, watcher 10.
`app` additionally depends on `deagle` and `setup-wizard-lib` directly.

### 3.4 Runtime relations: five contracts, all defined in `shared`

| Contract | Defined in | Producer | Consumer | Purpose |
|---|---|---|---|---|
| `Modules.MODULE_ENGINE` (`com.oasisfeng.island.fdroid`) | `util/Modules` | n/a | `DevicePolicies`, `DeviceAdmins`, `MainActivity` | Hard-coded package name of the DPC |
| `CATEGORY_PARENT_PROFILE` / `CATEGORY_MANAGED_PROFILE` | `engine/CrossProfile` | `app` (`MainActivity`) | `engine` (`IslandProvisioning`, via `Modules.getMainLaunchActivity`) | Engine launches the UI without knowing it |
| `PersistentService.SERVICE_INTERFACE` | `PersistentService` | `app` (`ShortcutSyncService`), `installer` (`AppSettingsHelperService`) | `engine` (`IslandPersistentService`), `IslandSettings` | Plug-in style long-running services bound by `engine`, toggled by settings |
| `Api` action constants (`...action.FREEZE` etc.) | `api/Api` | external apps | `engine` (`ApiActivity`, `ApiReceiver`, `ApiDispatcher`) | Public intent API, guarded by `FREEZE_PACKAGE`, `LAUNCH_PACKAGE`, `SUSPEND_PACKAGE` |
| `Shuttle` / `ShuttleProvider` | `shuttle/*` + 4 AIDL | `shared` | `app` (8 files), `installer` (2), `watcher`, `open`, `shared`; `engine` only calls `ShuttleProvider.initialize` | Cross-profile RPC |

Other links:

- `installer` is entered from `app` (`IslandAppClones`) through an intent carrying `InstallerExtras` (type in `shared`) and `addCategory(packageName)`.
- `app` starts provisioning only through the system `ACTION_PROVISION_MANAGED_PROFILE/DEVICE` intents. The system then starts `engine`'s `IslandProvisioning`, which launches the UI back.
- `PersistentService` binding is limited to services of the same UID (`serviceInfo.applicationInfo.uid == myUid`).

### 3.5 Typical flows

```
Setup      SetupActivity (app) ─ ACTION_PROVISION_MANAGED_PROFILE ─▶ system
           ─▶ IslandProvisioning (engine): prune apps, cross-profile filters, ShuttleProvider.initialize
           ─▶ launches MainActivity (app) through CATEGORY_PARENT_PROFILE
Control    AppListFragment ─▶ IslandAppControl (app) ─▶ Shuttle ─▶ profile process
           ─▶ DevicePolicies.setApplicationHidden / setPackagesSuspended
External   3rd-party app ─ FREEZE/LAUNCH intent ─▶ ApiActivity/ApiReceiver (engine) ─▶ IslandManager
Delegation 3rd-party app ─ BIND_SYSTEM_SERVICE ─▶ SystemServiceBridge (open, :api process)
```

## 4. Current problems

1. **`shared` carries module-specific code.** Measured by which modules reference each file:

   | File | Real users | Belongs to |
   |---|---|---|
   | `provisioning/SystemAppsManager` | engine, app | engine (`app` also uses it) |
   | `installer/InstallerExtras` | installer, app | installer |
   | `analyzer/ApkAnalyzer` | installer | installer |
   | `shuttle/ActivityShuttle` | installer | installer |
   | `util/RomVariants` | installer | installer |
   | `home/HomeRole` | watcher | watcher (stays with the engine) |
   | `IslandNameManager` | app, watcher | `app` |
   | `util/AppInfo`, `data/helper/ApplicationInfoExtensions` | app | `app` |
   | `util/ModuleContext` | installer, app | `app` |
   | `shuttle/ContextShuttle` | app | `app` |
   | `engine/IslandManager` | engine, installer, app | stays in the engine |

   Files with **no** user outside `shared`: `ShortcutIcons`, `UserPrefixedLog`, `MethodInvocation` (Java copy of the AIDL type), `AppSettingsProvider` (manifest-registered).
2. **Package names do not match modules.** `shared` holds `...island.engine.*`, `...island.installer.*` and `...island.provisioning.*`; `app` holds `...island.engine.ClonedHiddenSystemApps` and its namespace is `...island.mobile`; the instrumented test is in `org.securesystem.insular`.
3. **String-based coupling the compiler cannot check:**
   - `MainActivity.onCreateInProfile()` finds engine's receiver with `name.startsWith("com.oasisfeng.island.provision")`; `ManualProvisioningReceiver` carries a comment that its package must keep that prefix.
   - `DeviceAdmins` falls back to the literal `"com.oasisfeng.island.IslandDeviceAdminReceiver"`.
   - `Modules.MODULE_ENGINE` is a constant that must equal the application ID. It is valid now that only one variant exists.
   - `ApiActivityTest` (`engine/src/androidTest`) hard-codes `setPackage("com.oasisfeng.island.fdroid")`.
4. **Resources are reached across modules** through `com.oasisfeng.island.shared.R` (about 13 places, e.g. `mainland_name`, `accent`).
5. **Duplicate Java/Kotlin pairs** in `shared`: `NotificationIds` and `Hacks` exist as both `.java` and `.kt`; `MethodInvocation` exists as both AIDL and Java.
6. **No frontend/backend boundary.** `app` is meant to be the UI, but 22 of its source files call DPC-level APIs directly (`DevicePolicies`, `DevicePolicyManager`, `IslandManager`, `Hacks`). Calls counted: `policies.invoke` x15, `policies.execute` x10, `IslandManager.launchApp` x4, `setApplicationHidden` x5, plus single calls such as `setPackagesSuspended`, `setPermissionGrantState`, `setCrossProfilePackages`, `setUserRestriction`, `setPersistentPreferredActivity`, `addCrossProfileWidgetProvider` and support-message setters. Heaviest files:

   | File (under `app`) | Lines touching DPC APIs |
   |---|---|
   | `setup/SetupViewModel`, `setup/IslandSetup` | 21 each |
   | `controller/IslandAppControl` | 14 |
   | `controller/IslandAppClones` | 12 |
   | `settings/IslandSettingsActivity`, `settings/AppOpsPermissionsUnlock` | 11 each |
   | `adb/AdbSecure` | 10 |
   | `MainActivity` | 9 |
   | `settings/GeneralPreferenceFragment`, `adb/ProfileRestrictionsSync` | 6 each |

   Shuttle makes this possible: the UI passes a lambda that runs inside the profile, so DPC logic lives in UI files.
7. **Firebase and analytics are already dead code (removal deferred, D7).** No Firebase or GMS SDK is on any classpath. What remains is scaffolding:
   - `shared/firebase/FirebaseWrapper`, `shared/.../NotificationManagerExtender` (used only by the wrapper) and the `condom` dependency (used only by the wrapper). `Config.kt` imports `FirebaseWrapper` but never uses it.
   - `AnalyticsImpl.reportEvent` and `setProperty` are empty; every `CrashReport` method except `initCrashHandler` is empty, and `initCrashHandler` installs a handler that only re-delegates.
   - Build leftovers: `CRASHLYTICS_ENABLED` build-config fields and the `firebase_crashlytics_collection_enabled` meta-data in `shared`, `firebasePerformanceInstrumentationEnabled` in `gradle.properties`, a GMS `-dontwarn` in `app/proguard-rules.pro`, `analytics_tracker.xml` excluded in `shared/build.gradle`.
   - At least 68 call sites still go through the `Analytics` abstraction: `event(...)` 39, `logAndReport` 19, `report` 8, `trace` and `setProperty` 2. Only `logAndReport` (a `Log.e`) and `Analytics.log` (a `Log.i`) have any effect.
   - **Not removable:** the GMS package names in `SystemAppsManager` and `WellKnownPackages`. They are not an SDK dependency. They decide which system apps stay inside the work profile (the comment there explains that disabling GMS in the profile kills the owner user's GMS and causes ANRs).
8. **Adjacent issues in `Config.kt` (reported only).** `Config.get()` reads the hard-coded `config` map and ignores each entry's `default`. As a result `URL_PLAY_ALPHA` returns an empty string (its key is not in the map), `URL_SETUP_MANAGED_MAINLAND` returns the map's `#manual-setup-for-island-in-god-mode` anchor instead of its own `#activate-managed-mainland` default, and the `"hhttps://..."` typo in `URL_SETUP_TROUBLESHOOTING`'s default is never used. `IS_REMOTE`, `isRemote()` and `Analytics.Property.RemoteConfigAvailable` are Firebase Remote Config leftovers. `SettingsActivity` still branches on `GooglePlayStore.PACKAGE_NAME` to show the Play alpha group link (deferred with D7).

## 5. Target structure

### 5.1 Principles

1. **Frontend/backend split (D5).** `engine` is the DPC backend: the only place that calls `DevicePolicyManager`, `AppOps`, `PackageInstaller` and cross-profile state, exposed through a small typed facade. `app` is a pure frontend: it renders state, collects user intent and calls the facade. The dependency is `app → engine`, never the reverse. The engine reaches the UI only through the intent contract (`CATEGORY_PARENT_PROFILE`, `CATEGORY_MANAGED_PROFILE`), and the UI contributes background services only through the `PersistentService` action.
2. **Complexity decides merges (D2).** Each decision in 5.2 is backed by measurement.
3. **`engine` is Compose-free (D6).** See 5.3 for the verified inventory and the rule that keeps it so.
4. The `engine`/`mobile` flavors were the only reason the UI module could not import `engine`. With D4 that constraint is gone.

### 5.2 Merge decisions

Resource-name collisions were checked for every candidate pair (strings, colors, dimens, styles, layouts, drawables, ...). There are **none**, so no merge needs resource renaming.

| Module | Decision | Evidence |
|---|---|---|
| `sideplay` | Deleted (done) | Manifest only, Google Play plumbing. Its permissions now live in `installer`'s manifest |
| `assembly` → `app` | Merged (done) | No source. Only build configuration, one manifest and a few resources. See 5.4 |
| `shared` → `engine` | Merge | All other modules depend on it and it depends on none of them, so it is already the base of the engine. Its extra dependencies (`okhttp`, `apk-parser`) are used only by `ApkAnalyzer`, which only the installer uses, so they move to `app` with the installer. Remaining work is mechanical: `shared.R` is referenced in about 13 places and `shared.BuildConfig` in 2 |
| `watcher` → `engine` | Merge | 2 classes, 4 resource files, depends only on `shared`, no UI |
| `open` → `engine`, as package `open` | Merge | Depends only on `shared`, 4 resource files, no per-module ProGuard rules (the only ProGuard file is `app`'s, 8 effective lines). Process isolation (`android:process=":api"`) is a manifest attribute, not a module property, so merging changes nothing at runtime. Neutral on complexity, so the lower module count wins |
| `installer` → `app`, whole module | Merge | It holds the only real View UI among the engine candidates, and that UI cannot be split off cheaply. See 5.3 |

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
| 1 and 2 (`AppInstallerActivity`) | (a) Move the whole `installer` module to `app`. (b) Keep the activity in the engine and request the decision from an `app` activity through an intent and `startActivityForResult`. (c) Move only `AppInstallerActivity` to `app`, keep the other 5 files in the engine | **(a)** |
| 3 (`CompletionActivity`) | (a) Move it to `app` and make `IslandProvisioning.proceed` public as part of the profile-lifecycle facade; the `ACTION_PROVISIONING_SUCCESSFUL` intent filter moves to the `app` manifest. (b) Drop the dialog and rely on a notification | **(a)**. (b) changes behavior: this path runs `proceed` inside the activity and does not start the `IslandProvisioning` foreground service, so the dialog is the only progress indicator |

Why (a) for the installer, measured against D2:

- `AppInstallerActivity` is 415 lines (47% of the installer's 880) and mixes validation, caller qualification, session handling and both dialogs in one `prepare()` flow. Option (b) restructures `prepare()` into stages that cross an activity boundary. Option (c) leaves most of the logic in the frontend anyway and splits a module that is cohesive today.
- The other 5 files have no UI beyond a toast or a notification and are tied to the activity: `AppInstallerStatusReceiver`, `AppInstallationNotifier` and `AppInstallerUtils` are referenced by `AppInstallerActivity`, and `AppInfoForwarderActivity` by `AppInstallationNotifier`.
- Nothing else references `AppInstallerActivity` (only its manifest entry and an intent from `app`), so moving the module changes no call sites.
- Cost: `app` gains the installer's engine-level calls (`DevicePolicies` 5, `AppOpsCompat` 3, `ApkAnalyzer` 2, `IslandManager` 2, `Hacks` 1 and similar). They are handled by the facade work (5.6) like the DPC calls already in `app`.

#### Other findings

- **`deagle` is left untouched (D10), so Compose-freedom of the engine is enforced by rule, not by the compiler.** `shared` exposes `deagle` with `api`, so the engine's classpath still contains deagle's UI helpers and its `appcompat`, `material`, `recyclerview` and data-binding setup. Rule for the engine after the three sites move out:
  - It may use `com.oasisfeng.android.widget.Toasts` and `com.oasisfeng.android.ui.IconResizer`.
  - It must not import anything else from `com.oasisfeng.android.ui.*` (`Dialogs`, `WebContent`, `Snackbars`, `AboutActivity`), nor any `androidx` UI or Compose package.
  - The rule can be checked with a plain `grep` in CI over `engine/src`.
- **UI-facing resources live in `shared`:** colors `primary` and `accent`, strings `mainland_name`, `action_learn_more`, `device_admin_support_message_*`, icons `ic_landscape_black_24dp` and `ic_settings_applications_white_24dp`. The colors and icons are also used as notification tints and icons by the engine, so they stay in the engine. This is a layering smell, not a Compose blocker: Compose reads them through `stringResource` and `colorResource`.

### 5.4 `assembly` merged into `app` (done)

What was moved or decided:

- `mobile` became `app` (`:app`) and switched from `com.android.library` to `com.android.application`. It took over signing, minify, version, locale filters (`en`, `zh`, `zh-rTW`, `pt-rBR`), the ProGuard file, the `app_name` strings (en, zh), `backup.xml` and the debug manifest.
- The application manifest is the old `mobile` manifest plus the attributes of the old `assembly` manifests: `sharedUserId`, `label`, `allowBackup`, `fullBackupContent`. `tools:replace="android:label"` is required, because the engine library declares its own label.
- The duplicate `MainActivity` declaration of `assembly` (it existed only "to ensure Run/Debug in Android Studio") and the Google Play manifest were deleted. `google-services.json` was deleted because no plugin read it.
- **`sharedUserId` stays.** A single APK does not need it, but Android refuses to update an installed app whose `sharedUserId` changed or was removed, so dropping it would force every existing user to reinstall and lose the profile ownership. Code that enumerates packages of the same UID (`Modules.broadcast`, `bindPersistentServices`) keeps working and can be simplified later without touching the manifest.
- Not done yet: the CI and Fastlane files that referenced `assembly` were removed by the maintainer. The GitHub Actions workflow still has to be written, including the signing variables in 3.2.

### 5.5 Resulting structure

```
app       application   frontend: all UI, installer, CompletionActivity; signing, version, ProGuard, app resources
 └─ engine   library       backend, Compose-free: shared + watcher + open
     └─ deagle            external, untouched
(app also uses libs/setup-wizard)
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

`app` gains `installer/` (former module) and `CompletionActivity`, next to its existing UI packages.

### 5.6 Engine facade

Proposed grouping of the DPC calls found in `app`. The grouping is by file name and call usage, so each file needs checking before it is moved.

| Facade | Replaces direct calls in | Operations |
|---|---|---|
| App control | `IslandAppControl`, `IslandAppClones`, `ClonedHiddenSystemApps`, `IslandAppShortcut`, `FeatureAction`, `AppInstallerActivity` | hide/unhide, suspend, launch, enable system app, clone, freeze |
| Profile lifecycle | `SetupViewModel`, `IslandSetup`, `IslandSettingsActivity`, `MainActivity`, `CompletionActivity` | create, re-provision, destroy, activation checks, device owner vs profile owner |
| Security and permissions | `AdbSecure`, `ProfileRestrictionsSync`, `AppOpsPermissionsUnlock`, `GeneralPreferenceFragment`, `OpsManager` | user restrictions, app-ops, permission grants, cross-profile packages and widgets, support messages |
| State queries | `IslandAppListProvider`, `LiveProfileState`, `LiveUserRestriction`, `IslandAppInfo` | profile list, owner status, hidden/suspended state, observed as `LiveData` |

Remaining `app` files (`model/*`, `ui/*`, `widget/*`, `featured/*`, `guide/*`, `console/*`, `common/app/*`) are UI-only apart from a few single-line hits.

## 6. Migration plan

Each step ends with a full build. Steps run in the order of the table unless a status says otherwise.

| # | Change | Status | Risk |
|---|---|---|---|
| 1 | Delete `sideplay`, the `googleplay` and `normal` flavors, the `engine` and `mobile` packaging flavors and the Play lanes in `fastlane/Fastfile`. Move `UPDATE_PACKAGES_WITHOUT_USER_ACTION` into `installer`'s manifest | Done | Low |
| 2 | Convert the build to Kotlin DSL on the latest AGP and Gradle, merge `assembly` into `app` (5.4), rename `mobile` to `app`, make Gradle the only version source | Done | Medium |
| 3 | Fold `shared`, `watcher`, `open` into `engine` and `installer` into `app`, one module per commit. Single-user classes go to their owner: `ApkAnalyzer`, `ActivityShuttle`, `RomVariants`, `InstallerExtras`, `okhttp`, `apk-parser` to `app` with the installer; `AppInfo`, `ContextShuttle`, `IslandNameManager`, `ModuleContext` to `app`; the rest stays in `engine`. The Groovy scripts of the folded modules disappear, so `android.newDsl.optOut` only lists `:deagle` and `:setup-wizard-lib` afterwards | Next | Low to medium. `open` needs a manifest and ProGuard check |
| 4 | Replace string couplings (section 4, item 3) with compile-checked references: `MainActivity` and `DeviceAdmins` reference the receiver class directly, and `MODULE_ENGINE` becomes `context.packageName` or a build constant. Rename misleading packages including the `app` namespace, drop the duplicated Java/Kotlin pairs | Pending | Medium |
| 5 | Move `CompletionActivity` to `app` (5.3) | Pending | Low to medium. The intent filter and `directBootAware` move with it; behavior on a real provisioning run is unverified |
| 6 | Introduce the engine facade (5.6) and move DPC calls out of `app`, one facade at a time: state queries, app control, security and permissions, profile lifecycle (lowest to highest risk) | Pending | High. This is the real refactor |
| 7 | Write the GitHub Actions workflow (build, signing from secrets) and add the Compose-free check for `engine/src` (5.3) | Pending | Low |
| 8 | Deferred (D7): remove the Firebase scaffolding and the `Analytics` abstraction (section 4, item 7) and the Play-only branches (item 8) | Deferred | Medium for size: at least 68 call sites in `app` (41), `shared` (21), `engine` (13), `installer` (3), `open` (4). Mechanical |

After step 6, the Compose migration of `app` can start without touching the engine.

## 7. Open items

1. **AGP 10 and the external submodules** (3.2, known risk): decide whether `deagle` and `setup-wizard-lib` get a build script owned by this repository, or are updated upstream. This must be settled before moving to AGP 10.
2. **Release build and device run** were not verified. They need the signing keystore and a device with a work-profile capable ROM.
3. **README** still describes the old module layout (`assembly` as the build portal) and the sibling-directory `deagle` setup. It should be rewritten together with the GitHub Actions workflow.
4. **`CompletionActivity`**: step 5 keeps the progress dialog by moving the activity. Dropping the dialog is the alternative if the behavior change is acceptable.
