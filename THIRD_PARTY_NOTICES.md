# Third-party software

Konlyut's own source is licensed under MIT. Runtime dependencies remain under their own licenses; MIT does not replace their notices.

| Component | Role | License/source |
|---|---|---|
| AndroidX / Jetpack Compose / Material 3 | UI, lifecycle, DataStore, FileProvider | [Apache 2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| Kotlin and kotlinx.coroutines | Language runtime and concurrency | [Apache 2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| OkHttp / Okio | HTTPS client | [Apache 2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt) |
| Gson | JSON parser | [Apache 2.0](https://github.com/google/gson/blob/main/LICENSE) |

JUnit and Robolectric are test-only dependencies, not bundled as user-facing runtime libraries. Review transitive license and notice obligations again before public distribution. The project uses ExchangeRate-API's public data endpoint; its service terms are separate from the source-code license.
