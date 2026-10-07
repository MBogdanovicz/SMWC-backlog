# SMWC Backlog

A personal Android app to search romhacks on [SMW Central](https://www.smwcentral.net), see each hack's details, keep a backlog, and mark hacks as completed. The backlog is synced to Google Drive, and hacks with a [RetroAchievements](https://retroachievements.org) set are flagged, with your progress in each set.

## Structure

| Module | Contents |
| --- | --- |
| `core/` | Plain Kotlin, no Android. SMW Central API client (`ajax.php`), the backlog model with cross-device merge, the Google Drive client (`appDataFolder`), and the RetroAchievements client and hack matching. Has unit tests. |
| `app/` | Jetpack Compose Android app with Search, Backlog and Achievements tabs plus hack and set details, local storage in `backlog.json`, and Google sign-in for Drive. |

### How the backlog works

- The whole backlog is a single JSON document (`BacklogDocument`), stored on the device and uploaded as `backlog.json` to the app's hidden Drive folder (`drive.appdata` scope). The file is not visible in your Drive, and the app cannot see your other files.
- Every hack keeps an `updatedAt` timestamp. On sync, the most recent change wins per hack. Removed hacks become tombstones, so a removal made on one device is not undone by a sync from another.
- Each entry keeps the hack's release date (when SMW Central accepted it) and number of exits, for sorting; entries added before that are filled in once in the background. The sort order (added, name, release date or exits, either direction) is chosen on the Backlog tab and saved per device.
- The Backlog tab can be filtered by difficulty, minimum rating, RetroAchievements set (when an account is set up) and release year; the filter is saved per device. Release date, exits and rating are re-read from SMW Central weekly in the background, since ratings change.
- Every hack is in one of three lists: Backlog, Playing or Completed, and can have personal notes (synced with the backlog).
- New versions of a hack are new files on SMW Central (the old one points to the new one). The weekly details refresh notices it: the entry shows "New version on SMW Central", and its details page offers to switch to the new version, keeping status, dates and notes.
- When RetroAchievements reports a beaten, completed or mastered set for a hack that is not completed, the Achievements tab and the set page offer to mark it as completed ("Not now" is remembered per device).
- Hacks that are not on SMW Central (e.g. only in the author's files) can be added by name from the end of the search results. They are flagged `custom`, get a negative id derived from the name (so the same name added on two devices is one entry), and have no details page.
- Sync runs when the app opens, a few seconds after each change, and from the ⟳ button on the Backlog tab.

### SMW Central API

The API has no official documentation (it is the one the site itself uses):

- Search: `ajax.php?a=getsectionlist&s=smwhacks&n=<page>&o=date&d=desc&f[name]=<term>` (50 per page)
- Details: `ajax.php?a=getfile&v=2&id=<id>`

The site rate-limits aggressively, so `SmwcClient` spaces calls 1.5 s apart, caches hack details, and only searches when you submit the query (not on every keystroke). The parser is lenient: every field is optional.

The name filter only matches the exact text as a substring, so `HackSearch` adds a tolerant fallback: when the exact text finds nothing, it asks for smaller runs of the typed words (longest first) and keeps the names that contain every word in order, ignoring case, accents and punctuation ("Ja Tack" finds "Ja, tack!", "Cube's" finds "Cube´s"). It makes at most 6 extra requests per search.

### RetroAchievements

- The list of SNES games with achievements (`API_GetGameList.php?i=3&f=1&h=1`) is downloaded at most once a week and kept in the app's cache folder; only titles tagged `~Hack~` are used, and subsets are skipped.
- A hack has a set when its ROM hash is one of the set's hashes or, failing that, when the names match ignoring case, punctuation, the `~Hack~` tag and a leading "Super Mario World". A name-only match is labeled "matched by name", since it may be another version of the hack.
- ROM hashes need your clean Super Mario World (USA) ROM, chosen once in the Achievements tab (with or without copier header; it is checked by MD5 and stays in the app's private storage). For each backlog hack the app downloads the patch from SMW Central, applies it in memory and hashes the result as RetroAchievements does (MD5 without copier header). Hashes are kept in `rom_hashes.json` per download URL, so each version of a hack is patched once.

### Patching

`core/patch` applies BPS (checked against its CRC32s) and IPS patches. IPS has no checksums and old ones often expect a headered ROM, so both placements are tried and the one whose internal SNES checksum is valid comes first. `HackPatcher` downloads a hack, finds the patches in the zip and returns the patched ROMs. The details screen's **Apply patch** uses it: it asks for the clean ROM if needed, lets you pick a patch when the download has several, and saves the `.sfc` wherever you choose.
- The Achievements tab can be sorted by name, number of achievements or points (either direction) and filtered by list (backlog/completed) and mastery (every achievement unlocked in hardcore); the choices are saved per device.
- Progress comes from `API_GetGameInfoAndUserProgress.php` and is kept in memory until refreshed.

## Google Drive setup (one time)

1. In the [Google Cloud Console](https://console.cloud.google.com/), create a project and enable the **Google Drive API**.
2. Under **Google Auth Platform → Branding/Audience**, set the consent screen to *External*, keep it in **Testing**, and add your email as a *test user*. Personal use needs no Google verification.
3. Under **Data Access**, add the `.../auth/drive.appdata` scope.
4. Under **Clients**, create two OAuth clients of type **Android**, one per build (they have different package names and signing keys):
   - release: package `com.capivaraec.smwc` and the SHA-1 of the release key (see "Release build" below; `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` prints it)
   - debug: package `com.capivaraec.smwc.debug` and the SHA-1 of the shared debug key, `DA:E5:0E:8C:03:10:52:FD:AE:93:A0:F7:E1:B2:29:2C:0E:5D:C1:8B` (to check it: `keytool -list -v -keystore <path>/debug.keystore -alias androiddebugkey -storepass android`)

Debug builds are signed with a shared debug key, so their SHA-1 is the same on every machine. The key is not in the repository (it is public, and anyone with the key could sign APKs that Android treats as the debug app): keep `debug.keystore` in a private place (e.g. a cloud folder) and either point to it from `keystore.properties` with `debugStoreFile=/path/to/debug.keystore` (or the `SMWC_DEBUG_STORE_FILE` environment variable), or copy it to `app/debug.keystore`. Without it, debug builds use the Android SDK's own debug key, whose SHA-1 would have to be registered as well. There is no other key or config file to add to the app: Google Play Services recognizes the app by package name + SHA-1.

## RetroAchievements setup (optional)

In the app's **Achievements** tab, enter your RetroAchievements user name and Web API key (retroachievements.org → Settings → Keys). The app checks them with RetroAchievements before saving. They are stored only on the phone, encrypted with a key kept in the Android Keystore, in a folder excluded from Android's cloud backup. The API cannot tell whose key it is, so the user name is needed for your progress.

## Release build

The debug build is a separate app (`com.capivaraec.smwc.debug`, "SMWC Backlog (debug)"), so it can be installed next to the release one.

The release build is shrunk with R8 and signed with your own key, which is never committed:

1. Create the key once (keytool asks for a password; keep the key and the password backed up, since updates must be signed with the same key):

   ```sh
   keytool -genkeypair -v -keystore ~/.android/smwc-release.jks -alias smwc -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=SMWC Backlog"
   ```

2. Put its location and password in `keystore.properties` in the project root (in `.gitignore`), or in the environment variables `SMWC_STORE_FILE`, `SMWC_STORE_PASSWORD`, `SMWC_KEY_ALIAS` and `SMWC_KEY_PASSWORD`:

   ```properties
   storeFile=/Users/you/.android/smwc-release.jks
   keyAlias=smwc
   storePassword=…
   keyPassword=…
   ```

3. Build and install it: `./gradlew :app:assembleRelease` (output in `app/build/outputs/apk/release/`) or `./gradlew :app:installRelease`.

Without the key, the release build is left unsigned.

### Publishing a release (GitHub Actions + Obtainium)

Pushing a version tag builds, tests and signs the APK on GitHub and attaches it to a GitHub release, where Obtainium finds it:

```sh
git tag v0.2.0
git push origin v0.2.0
```

The version comes from the tag (`v1.2.3` → versionName 1.2.3, versionCode 1002003), so `app/build.gradle.kts` does not need editing; tags must look like `v1.2.3` and always go up. The workflow (`.github/workflows/release.yml`) runs in the `release` environment, which waits for the owner's approval (Actions → the run → Review deployments → Approve) and holds the signing secrets.

One-time setup in the repository's Settings:

1. Environments → New environment `release` → Required reviewers: yourself (with "Prevent self-review" off). Optionally, under Deployment branches and tags, allow only tags matching `v*.*.*`.
2. In that environment, add these secrets:
   - `RELEASE_KEYSTORE_BASE64`: the release key encoded as text, e.g. `base64 -i ~/.android/smwc-release.jks | pbcopy`
   - `RELEASE_KEY_ALIAS`: `smwc`
   - `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD`: the key's password (the same for both)

In Obtainium, add the app from the repository's URL; it then updates from each new release.

## Running

Open the folder in Android Studio and run the `app` configuration on a device running Android 13 (API 33) or later with Google Play Services (the AYN Thor runs Android 13).

Tests for the `core` module (no Android SDK needed):

```sh
./gradlew -Psmwc.coreOnly :core:test
```

App unit tests and Android lint:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Next steps

- Confirm the real API fields on a device (the test fixtures in `core/src/test/resources` follow the reported format but could not be checked live).
- Search filters (difficulty, sort order) and an on-disk cache for searches.
