# ==============================================================================
# Never Land Server Manager - Regras do ProGuard / R8 (proguard-rules.pro)
# ==============================================================================
# Regras de ofuscação e preservação de símbolos de reflexão.
# Em modo de depuração (debug), a minificação permanece desligada (isMinifyEnabled = false).
# ==============================================================================

# Preserva provedores criptográficos Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Preserva classes do cliente SSHJ
-keep class com.hierynomus.sshj.** { *; }
-dontwarn com.hierynomus.sshj.**
