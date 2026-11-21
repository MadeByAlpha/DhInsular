# Insular Architecture

Insular is a fork of [Island](https://github.com/oasisfeng/island). The app itself becomes the Device Policy
Controller (DPC) of an Android Work Profile, so no external MDM is required.

## 1. Core idea

1. The app is **Profile Owner** (or Device Owner) of a managed profile and drives `DevicePolicyManager` directly.
2. Android blocks direct calls between the owner user and the managed profile, so the app uses **Shuttle**: a lambda
   (`Context.() -> R`) is serialized as a `Closure`, sent through a cross-profile `ContentProvider` call, and executed
   in the other profile.
3. Everything ships as one APK and one UID (`sharedUserId="com.oasisfeng.island"`, kept so existing installs can
   update without losing profile ownership).

## 2. Decisions

- `libs/deagle` and `libs/setup-wizard` are external and are **not modified**.
- `app` is a **frontend** that only controls the DPC. Its UI moves to **Compose** after the backend boundary exists.
- Firebase, Analytics and every other GMS-related part are removed **last** (step 8).
- Build scripts use Kotlin DSL; CI is rewritten for GitHub Actions.

## 3. Structure

Current:

```
app ──▶ engine, installer, watcher, open ──▶ shared ──▶ hidden (compileOnly)
```

Target (after step 6): `engine` and `shared` are merged last, under the name `shared`.

```
app ──▶ shared ──▶ hidden (compileOnly)
```

| Module | Responsibility | Must not contain |
|---|---|---|
| `hidden` (`libs/hidden`) | `compileOnly` hidden API classes. Migration done | Anything else |
| `shared` | DPC backend: `DevicePolicies`, `Users`, Shuttle, provisioning, public intent API, `AppSettings`, watcher, APK installer, external app requests (`open`) | Layout and Compose UI. Deagle-based UI (`Dialogs`, `Toasts`, ...) is allowed |
| `app` | Frontend: all layout/Compose UI, setup wizard, shortcuts, settings. Owns signing, version, ProGuard, app resources | DPC logic (see 5.1) |

Remaining modules, absorbed into `shared` before the final `engine` + `shared` merge. None of them depends on the `engine` module (only `engine` depends on `shared`, never the reverse); `installer` imports `island.engine.IslandManager`, but that class is physically in `shared`, only its package name says `engine`. Indirect references were checked too and found none: all 4 AIDL files are in `shared`; resources the three modules use but do not define (`accent`, `primary`, `since_api_*`, `ic_landscape_black_24dp`, ...) come from `shared`, never `engine`; no `Class.forName`/`ServiceLoader`/class-name strings point into `engine`; their manifests and ProGuard reference no `engine` component; `engine` does not reference them either. The only runtime links are intent actions (`PersistentService`, `BIND_SYSTEM_SERVICE`). The rule for a new module: if it depends on `engine`, merge it into `engine` first.

| Module | Files | Notes |
|---|---|---|
| `watcher` | 8 | 2 classes, 4 resource files, no UI |
| `installer` | 13 | Only UI is two Deagle `Dialogs` in `AppInstallerActivity.prepare()`; replace `dialog_checkbox.xml` with a code-built `CheckBox` and delete the layout |
| `open` | 15 | Becomes package `open`. All backend (binder proxies, delegation). `SystemServiceBridge` process isolation is a manifest attribute, unaffected by the merge |
| `engine` | 42 | DPC core. Merged with `shared` last |
| `shared` | 76 | Old base library |

Resource names do not collide between these modules.

## 4. Build

- Gradle 9.9.2, AGP 9.5.2, Kotlin 2.4.21, JDK 17+ (verified with 21). `compileSdk 35`, `minSdk 27`, `targetSdk 34`.
- `app` uses the new DSL and built-in Kotlin. The other modules, `deagle` and `setup-wizard-lib` still use the old DSL
  and `kotlin-android` (Groovy scripts), so `gradle.properties` sets `android.builtInKotlin=false` and
  `android.newDsl.optOut`.
- `applicationId` is `com.oasisfeng.island.fdroid` (equals `Modules.MODULE_ENGINE`). No flavors.
- Release signing reads `secrets.properties` at the repository root, otherwise `KEYSTORE_PASSWORD`, `KEY_PASSWORD`,
  `KEY_ALIAS`; keystore is `android-signing-keystore.jks` at the repository root.
- Build: `JAVA_HOME=<JDK 17+> ./gradlew :app:assembleDebug`.
- **Not verified:** a release build and any run on a device (needs the keystore and a work-profile capable ROM).
- **Risk:** the opt-out properties are removed in AGP 10, and `deagle` and `setup-wizard-lib` would stop building.
  Decide between updating them upstream or building them with a script owned by this repository.

## 5. Runtime contracts

| Contract | Producer | Consumer | Purpose |
|---|---|---|---|
| `Modules.MODULE_ENGINE` | n/a | `DevicePolicies`, `DeviceAdmins`, `MainActivity` | Hard-coded package name of the DPC |
| `CATEGORY_PARENT_PROFILE` / `CATEGORY_MANAGED_PROFILE` | `app` (`MainActivity`) | `engine` (`IslandProvisioning`) | The backend launches the UI without knowing it |
| `PersistentService.SERVICE_INTERFACE` | `app`, `installer` | `engine` (`IslandPersistentService`) | Plug-in style long-running services, same UID only |
| `Api` actions (`FREEZE`, `LAUNCH`, ...) | external apps | `engine` (`ApiActivity`, `ApiReceiver`) | Public intent API, guarded by `FREEZE_PACKAGE`, `LAUNCH_PACKAGE`, `SUSPEND_PACKAGE` |
| `Shuttle` / `ShuttleProvider` + 4 AIDL | `shared` | `app`, `installer`, `watcher`, `open` | Cross-profile RPC |

## 6. Known problems

1. **No frontend/backend boundary.** 22 source files of `app` call DPC-level APIs directly (`DevicePolicies`,
   `DevicePolicyManager`, `IslandManager`, `Hacks`) through Shuttle lambdas. Heaviest: `setup/SetupViewModel` and
   `setup/IslandSetup` (21 lines each), `controller/IslandAppControl` (14), `controller/IslandAppClones` (12),
   `settings/IslandSettingsActivity` and `settings/AppOpsPermissionsUnlock` (11 each), `adb/AdbSecure` (10),
   `MainActivity` (9).
2. **Module-specific code sits in `shared`.** Move to `app`: `AppInfo`, `ApplicationInfoExtensions`, `ContextShuttle`.
   Unused outside `shared` (candidates for removal or relocation): `ShortcutIcons`, `UserPrefixedLog`, `MethodInvocation`,
   `AppSettingsProvider` (manifest-registered).
3. **Package names do not match modules.** `shared` holds `...island.engine.*`, `...island.installer.*` and
   `...island.provisioning.*`; `app` holds `...island.engine.ClonedHiddenSystemApps` and its namespace is
   `...island.mobile`; the instrumented test is in `org.securesystem.insular`.
4. **String-based coupling the compiler cannot check:**
   - `MainActivity.onCreateInProfile()` finds the receiver with `name.startsWith("com.oasisfeng.island.provision")`;
     `ManualProvisioningReceiver` must keep that prefix.
   - `DeviceAdmins` falls back to the literal `"com.oasisfeng.island.IslandDeviceAdminReceiver"`.
   - `Modules.MODULE_ENGINE` must equal the application ID.
   - `ApiActivityTest` hard-codes `setPackage("com.oasisfeng.island.fdroid")`.
5. **UI resources live in the backend.** `com.oasisfeng.island.shared.R` is referenced across modules in about 13 places
   (`mainland_name`, `accent`, ...). Colors and icons also serve as notification tints and icons, so they stay in `shared`.
6. **Duplicate Java/Kotlin pairs** in `shared`: `NotificationIds` and `Hacks` (`.java` and `.kt`), `MethodInvocation` (AIDL and Java).
7. **Firebase and analytics are dead code.** No Firebase or GMS SDK is on any classpath.
   - Scaffolding: `FirebaseWrapper`, `NotificationManagerExtender`, the `condom` dependency, empty `AnalyticsImpl` and
     `CrashReport` methods, `CRASHLYTICS_ENABLED` fields, `firebase_crashlytics_collection_enabled` meta-data,
     `firebasePerformanceInstrumentationEnabled`, a GMS `-dontwarn`.
   - At least 68 call sites go through `Analytics`; only `logAndReport` and `Analytics.log` have any effect.
   - **Not removable:** the GMS package names in `SystemAppsManager` and `WellKnownPackages`. They decide which system
     apps stay in the work profile (disabling GMS there kills the owner user's GMS and causes ANRs).
8. **`Config.kt`** (report only): `Config.get()` ignores each entry's `default`, so `URL_PLAY_ALPHA` returns an empty
   string and `URL_SETUP_MANAGED_MAINLAND` returns the wrong anchor; the `"hhttps://..."` typo is never used;
   `IS_REMOTE`, `isRemote()` and `Analytics.Property.RemoteConfigAvailable` are Remote Config leftovers;
   `SettingsActivity` still branches on `GooglePlayStore.PACKAGE_NAME`.

## 7. Rules

1. **`hidden`** exposes hidden APIs and does nothing else.
2. **`shared` has no layout or Compose UI.** Forbidden: any `androidx` UI or Compose package, and layout, menu or theme
   resources. Deagle-based UI (`Dialogs`, `WebContent`, `Toasts`, `IconResizer`, ...) and notifications are allowed.
   Since `shared` exposes Deagle with `api`, this is enforced by convention and checked with a plain `grep` over
   `shared/src` in CI.
3. **`app` is a pure frontend.** It renders state, collects user intent and calls `shared`. The dependency is
   `app → shared`, never the reverse. The backend reaches the UI only through the intent contract
   (`CATEGORY_PARENT_PROFILE`, `CATEGORY_MANAGED_PROFILE`), and the UI contributes background services only through
   `PersistentService`.

### Backend facade (proposal for step 7)

Grouping of the DPC calls found in `app`; each file needs checking before it is moved.

| Facade | Replaces direct calls in | Operations |
|---|---|---|
| App control | `IslandAppControl`, `IslandAppClones`, `ClonedHiddenSystemApps`, `IslandAppShortcut`, `FeatureAction` | hide/unhide, suspend, launch, enable system app, clone, freeze |
| Profile lifecycle | `SetupViewModel`, `IslandSetup`, `IslandSettingsActivity`, `MainActivity` | create, re-provision, destroy, activation checks, device owner vs profile owner |
| Security and permissions | `AdbSecure`, `ProfileRestrictionsSync`, `AppOpsPermissionsUnlock`, `GeneralPreferenceFragment`, `OpsManager` | user restrictions, app-ops, permission grants, cross-profile packages and widgets, support messages |
| State queries | `IslandAppListProvider`, `LiveProfileState`, `LiveUserRestriction`, `IslandAppInfo` | profile list, owner status, hidden/suspended state, observed as `LiveData` |

## 8. Migration plan

Each step ends with a full build.

| # | Change | Status |
|---|---|---|
| 1 | Drop Google Play flavors; Kotlin DSL; merge `assembly` into `app`; rename `mobile` to `app` | Done |
| 2 | Collect hidden API classes into `hidden` | Done |
| 3 | Merge `watcher` into `shared` | Next |
| 4 | Merge `installer` into `shared`; replace `dialog_checkbox.xml` with a code-built `CheckBox` | Pending |
| 5 | Merge `open` into `shared` as package `open`. Check manifest and ProGuard; `SystemServiceBridge` keeps its separate process | Pending |
| 6 | Fold `shared` and `engine` into one module named `shared` | Pending |
| 7 | Move single-user classes (6.2) into `app`; replace string couplings (6.4) with compile-checked references; fix package names and the `app` namespace (6.3); drop duplicate Java/Kotlin pairs (6.6) | Pending |
| 8 | Introduce the backend facade and move DPC calls out of `app`, one facade at a time: state queries, app control, security and permissions, profile lifecycle. This is the real refactor | Pending |
| 9 | Write the GitHub Actions workflow (build, signing from secrets) and the UI-free check for `shared/src` (rule 7.2) | Pending |
| 10 | Remove the Firebase scaffolding, the `Analytics` abstraction and Play-only branches (6.7, 6.8) | Pending |

After step 8, the Compose migration of `app` can start without touching `shared`.

## 9. Open items

1. **AGP 10 and the external submodules** (section 4).
2. **Release build and device run** are unverified.
3. **README** still describes the old module layout (`assembly` as the build portal) and the sibling-directory `deagle`
   setup. Rewrite it together with the GitHub Actions workflow.
