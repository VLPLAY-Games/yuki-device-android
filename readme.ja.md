# Yuki Device Android

Yuki エコシステム向けの Android リモート制御クライアントです。フォアグラウンドサービスが `yuki-core` への接続を維持し、サーバーから送られてきたコマンド（懐中電灯、音量、通知、URL を開くなど）を実行します。接続設定と、どのコマンドを許可するかを選択するための小さな UI を備えています。

## 必要要件

Android Studio、Kotlin、Gradle（wrapper 同梱 - `./gradlew assembleDebug`）。正確な依存バージョン（AGP、Kotlin、Compose BOM など）は `gradle/libs.versions.toml` を参照してください。

## 設定

アプリの UI から設定します: サーバーアドレス（`ws://host:port` または `wss://host:port`）、デバイス ID、認証トークン、どの許可コマンドを有効にするか - 以前のビルドにあったチェックボックスとは異なり、現在ではこれはコマンド実行前に実際に強制されるものであり、`hello` ハンドシェイクで単に通知されるだけではありません。設定は `SharedPreferences` に保存され、アプリはアプリデータをバックアップしません（`android:allowBackup="false"`）。そのため、トークンが `adb backup`/クラウドバックアップを通じて漏れることはありません。

エコシステムのデフォルトの通信方式が暗号化されていない `ws://` であるため、`usesCleartextTraffic` はデフォルトで有効のままです - `yuki-core` 側で TLS を有効にする場合は、`network_security_config.xml` でこれを厳格化し、TLS を必須にすることを検討してください。

## コマンド

懐中電灯/トーチの制御、音量、通知、画面の明るさ（読み取り専用）、`open_browser`（`http`/`https` の URL のみ受け付け - それ以外のスキームは任意の `Intent` に渡されることなく拒否されます）、そして `CommandHandler.kt` に定義されているその他すべて。

## プロトコル

Yuki Protocol `yuki/1.0` を、[`yuki-protocol`](../yuki-protocol) のコピーを同梱する代わりに Kotlin で直接実装しています（`YukiMessage.kt`/`YukiProtocol.kt`）- プロトコルが変更された際は手動で同期を取ってください。
