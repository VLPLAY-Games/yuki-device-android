# Yuki Device Android

An Android remote-control client for the Yuki ecosystem: a foreground service maintains the
connection to `yuki-core` and executes commands the server sends (flashlight, volume, notifications,
open a URL, ...), with a small UI to configure the connection and pick which commands are allowed.

## Requirements

Android Studio, Kotlin, Gradle (wrapper included - `./gradlew assembleDebug`). See
`gradle/libs.versions.toml` for exact dependency versions (AGP, Kotlin, Compose BOM, ...).

## Configuration

Set from the app's UI: server address (`ws://host:port` or `wss://host:port`), device id, auth
token, and which capabilities are enabled - unlike the checkbox in earlier builds, this is now
actually enforced before a command runs, not just advertised in the `hello` handshake. Settings are
stored in `SharedPreferences`; the app does not back up app data (`android:allowBackup="false"`),
so the token isn't exposed through `adb backup`/cloud backup.

`usesCleartextTraffic` stays enabled by default since the ecosystem's default transport is
unencrypted `ws://` - if you turn on TLS on the `yuki-core` side, consider tightening this via a
`network_security_config.xml` to require it.

## Commands

Flashlight/torch control, volume, notifications, screen brightness (read-only), `open_browser`
(only accepts `http`/`https` URLs - other schemes are refused rather than handed to an arbitrary
`Intent`), and whatever else is in `CommandHandler.kt`.

## Protocol

Implements Yuki Protocol `yuki/1.0` directly in Kotlin (`YukiMessage.kt`/`YukiProtocol.kt`) rather
than pulling in [`yuki-protocol`](../yuki-protocol) as a submodule (Gradle/Android has no natural
place to mount one) - the reference copy lives at `yuki-protocol/kotlin/`, keep this one in sync by
hand when either changes. Uses the legacy handshake (token sent directly in `hello`); `yuki-core`
also supports a challenge-response handshake that keeps the token off the wire entirely, which this
client doesn't use yet.

## License

GNU General Public License v3.0 (GPLv3), same as the rest of the Yuki ecosystem - see
[yuki-system](https://github.com/VLPLAY-Games/yuki-system) for details.
