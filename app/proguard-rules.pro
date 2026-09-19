# ResilientPay Proguard / R8 Rules
# Keep JNA classes and native dispatch
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }

# Keep UniFFI generated bindings and Rust FFI boundary
-keep class uniffi.** { *; }
-keepclassmembers class uniffi.** { *; }

# Keep Bouncy Castle Ed25519 parameters and signers
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
