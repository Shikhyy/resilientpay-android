# M7 Hardware-Backed Check

## Hardware Backing Status
**NOT AVAILABLE ON TEST DEVICE**

## Rationale
The current local development environment lacks the required Android SDK, Android NDK, and Java toolchains necessary to compile or emulate Android Keystore implementations. Specifically:
- `$ANDROID_HOME` is unset and the SDK is not installed in default locations.
- Attempting to run `assembleDebug` locally fails due to missing Android dependencies.
- True hardware-backed Keystore verification (StrongBox) requires either a physical Android device or an API 34+ high-fidelity emulator, neither of which are present in this CI/sandbox context.

## FFI Integration Fallback
Instead, the UniFFI bindings were correctly verified on the **Host Side** (Rust). The FFI payload perfectly matches the frozen CBOR test vectors (`EXPECTED_CBOR_HEX` generated in Gate 3). The Kotlin integration structural code (`ResilientPayCore.kt`, `HardwareKeyManager.kt`, and `Protocol.kt` Client Adapter) has been integrated into the repository.

## CI Configuration
The `.github/workflows/android.yml` CI pipeline has been designed to strictly separate:
1. `rust-host-tests`
2. `android-compile-test`
3. `android-unit-test`
4. `android-instrumentation-test` (using macOS runners for hardware-accelerated Keystore emulation).

## Next Steps
To run the signing test end-to-end, the CI pipeline must execute `connectedAndroidTest` on the Android repository.
