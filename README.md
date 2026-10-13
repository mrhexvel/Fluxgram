# FluxGram

FluxGram is an unofficial Telegram client for Android built on top of the
open-source [Telegram Android client](https://github.com/DrKLO/Telegram).
It keeps upstream behavior intact where possible and adds opt-in privacy,
local history, appearance, and Python plugin features.

FluxGram is not affiliated with or endorsed by Telegram.

## Features

- Local edit history and an archive of deleted messages.
- Optional local retention of attachments from deleted messages.
- Ghost Mode controls for online status, typing, read receipts, story views,
  delayed delivery, and silent sending.
- Profile and list badges backed by a cache-first appearance API.
- Full-trust Python plugins with an import-safe public SDK.
- Account-aware local storage and lifecycle handling.

Ghost Mode changes what this client sends in supported flows. It is not an
anonymity system and cannot control other clients, Telegram servers, or flows
which are explicitly outside its supported scope.

## Plugin SDK

The public Python SDK is maintained in
[mrhexvel/fluxgram-plugin](https://github.com/mrhexvel/fluxgram-plugin) and is included
here as the `sdk/python` Git submodule. Plugin code imports the public `fluxgram`
package. Android and Chaquopy integration remains internal to this repository.

Plugins are fully trusted code. FluxGram does not sandbox installed Python
plugins. Install plugins only when you trust their source and publisher.
SDK lifecycle checks, quotas, immutable models, and managed services improve
compatibility and reliability; they do not restrict what trusted plugin code
can do through Java or other in-process APIs.

Installing a plugin already imports its entrypoint to run storage migration
checks, even when "enable after installation" is off. Trust the code before
installing it, not only before enabling its regular runtime.

## Building

### Requirements

- Android Studio 2025.1.4
- Android SDK 36
- Android NDK 27.2.12479018
- JDK 21
- Python 3.12 for the repository test harness

### Checkout

Clone the repository with all submodules:

```bash
git clone --recurse-submodules https://github.com/mrhexvel/Fluxgram.git FluxGram
cd FluxGram
```

If the repository was cloned without submodules:

```bash
git submodule update --init --recursive
```

GitHub source archives do not include submodule contents and are not complete
build checkouts.

### Local configuration

Create the local configuration file:

```bash
cp fluxgram.local.properties.example fluxgram.local.properties
```

Obtain your own Telegram `api_id` and `api_hash` from
[my.telegram.org](https://my.telegram.org) and set them in
`fluxgram.local.properties`. The optional appearance API URL must use HTTPS.

Firebase configuration is intentionally not tracked. To use Firebase services,
create Android apps for `org.fluxgram.messenger` and
`org.fluxgram.messenger.beta`, then place your generated configuration at:

```text
TMessagesProj_App/google-services.json
```

Do not commit local properties, production signing keys, passwords, or service
credentials. The release keystore and values inherited from upstream are dummy
development data and must not be used to publish an application.

### Debug APK

Build the arm64 debug variant with the Gradle wrapper:

```bash
./gradlew :TMessagesProj_App:assembleAfatDebug -Parm64Only --console=plain
```

## Tests

Run the Python and Java harness tests from the repository root:

```bash
python3.12 -B -m unittest discover -s tests/python
```

Some tests compile and execute Java harnesses and therefore require `java` and
`javac` from a compatible JDK in `PATH`.

## Telegram API

- [Obtaining an API ID](https://core.telegram.org/api/obtaining_api_id)
- [Telegram API documentation](https://core.telegram.org/api)
- [MTProto documentation](https://core.telegram.org/mtproto)
- [Telegram security guidelines](https://core.telegram.org/mtproto/security_guidelines)

Applications built from this source must follow Telegram's API terms, use their
own credentials and branding, and clearly identify themselves as unofficial.

## License

FluxGram is distributed under the GNU General Public License v2.0. See
[`LICENSE`](LICENSE). The project includes code and dependencies from their
respective upstream projects under their applicable licenses.
