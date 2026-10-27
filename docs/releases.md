# CI and GitHub releases

## What is published

A `vX.Y.Z` tag starts the release workflow. It repeats all verification, signs
the verified release APK, checks its certificate against the expected release
identity, and publishes:

- `keroid-X.Y.Z-universal.apk`: a signed, non-debuggable APK containing the Go
  core and terminal JNI for **arm64-v8a** and **x86_64**. Android 7.0+ (API 24);
  32-bit devices are not supported.
- `SHA256SUMS`: run `sha256sum -c SHA256SUMS` beside the downloaded APK.
- GitHub's automatic **Source code (zip)** and **Source code (tar.gz)** links.

The source archives contain the tagged source, not generated AARs, native
libraries, APKs, reference clones or credentials. Rebuild instructions and
pinned tool versions are included. No AAB is needed for GitHub sideloading;
Play Store distribution would be a separate workflow.

## Workflows

- `ci.yml`: branch pushes, pull requests and manual runs.
- `verify.yml`: shared checks used by CI and releases. Runs Go race tests,
  release-check unit tests, actionlint, native library builds, debug/release
  APK builds, Android unit tests, lint and API 36 emulator tests. It checks the
  release APK's version and both native libraries for each supported ABI.
  After successful emulator tests it reinstalls and launches the debug app.
- `release.yml`: tag-only. Verification must pass before signing is allowed.
  The signing job uses the `release` environment and no write token. A separate
  publish job gets `contents: write`, but never sees signing secrets.

Actions are pinned to commit SHAs; Dependabot proposes weekly action updates.
Builds use the tools in `.mise.toml`, Android SDK 37.0, build-tools 37.0.0 and
NDK 29.0.14206865. CI keeps the hosted runner's SDK path instead of the local
path from mise. SDK packages and pinned tool versions must remain available
upstream; the first hosted build is the definitive check of a clean runner.

CI artifacts are for short-lived testing: debug APKs use an ephemeral debug
key, and unsigned release APKs cannot be installed directly. Neither is a
production release. CI explicitly sets `-Pkeroid.unsignedRelease=true` so build
jobs cannot accidentally use a local signing identity. Only the signed APK
attached to a GitHub release has the
stable sideload identity. Artifacts/reports expire after 3–7 days; release
assets do not.

## One-time GitHub setup

1. Create the destination repository and push the initial source commit.
   Enable Actions. Prefer `main` as the default branch.
2. Under **Settings → Environments**, create **release**. Restrict deployments
   to tags matching `v*` and enable required review where the repository plan
   supports it. Protect release tags against unauthorized creation/deletion,
   and require CI on the default branch using rulesets.
3. Use the **existing** PKCS12 sideload key documented in
   [development](development.md#release-builds). Back it up securely offline;
   never regenerate it for an already-installed app.
4. Add these **environment secrets** to `release`:

   | Secret | Value |
   |---|---|
   | `KEROID_KEYSTORE_BASE64` | Base64 of the existing `release.p12` |
   | `KEROID_KEYSTORE_PASSWORD` | Existing PKCS12 password (also the key password) |

5. Add these **environment variables** to `release`:

   | Variable | Value |
   |---|---|
   | `KEROID_SIGNING_CERT_SHA256` | SHA-256 fingerprint of the existing signing certificate (required; hex, optionally colon-separated) |
   | `KEROID_KEY_ALIAS` | `keroid-release` (default; override only if the existing key uses another alias) |

The workflow intentionally refuses unsigned releases, missing secrets and
unexpected certificates. Secrets are not passed to the shared build/test
workflow or PRs, nor are they placed in Gradle properties, command-line
passwords, logs, caches or uploaded artifacts. The signing job uses a temporary
directory with restrictive permissions and removes its keystore on exit.

With `gh` authenticated and the environment already configured, the owner can
upload the existing identity **without printing secret values**:

```sh
repo=OWNER/REPO
signing="$HOME/.local/share/keroid/signing"
base64 -w 0 "$signing/release.p12" |
  gh secret set KEROID_KEYSTORE_BASE64 --repo "$repo" --env release
gh secret set KEROID_KEYSTORE_PASSWORD --repo "$repo" --env release < "$signing/password"

# Export only the public certificate; hash the DER bytes, not the keystore.
fingerprint="$(keytool -exportcert -alias keroid-release -storetype PKCS12 \
  -keystore "$signing/release.p12" -storepass:file "$signing/password" |
  sha256sum | cut -d ' ' -f 1)"
gh variable set KEROID_SIGNING_CERT_SHA256 --repo "$repo" --env release --body "$fingerprint"
```

Do not use shell tracing (`set -x`) when handling these values. Never put the
base64 keystore in an issue, commit, workflow input or chat. Uploading the key
to GitHub is a deliberate trust decision; environment protections must be set
before pushing a release tag.

## Cutting a release

1. Update `version.properties`: set `versionName=X.Y.Z` and increment
   `versionCode` monotonically. The first release is `1.0.0` / code `1`.
   The tag must match exactly; prerelease tags are not currently supported.
2. Commit, push the branch, and wait for CI to pass.
3. Tag that verified commit and push the tag:

   ```sh
   git tag -a v1.0.0 -m "Keroid 1.0.0"
   git push origin v1.0.0
   ```

4. Review/approve the `release` environment deployment when prompted. The
   workflow publishes only after build/test success and signature checks.
5. Check the release assets, install/update on an emulator, and review the
   generated release notes. Phone installation is performed by the owner.

Never move/reuse an already-published version tag or overwrite its APK.
The publisher refuses an existing release rather than silently clobbering it.
If a run fails before publishing, rerun it after addressing the cause; source
changes require a new commit/tag and version. Keep the same signing identity
for every update, and do not try to fix signature errors by disabling checks.
