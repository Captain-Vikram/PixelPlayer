# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Specific rules for AutoValue if it's directly used or a transitive dependency
# (though usually AutoValue is a compile-time dependency and shouldn't need this)
# -keep class com.google.auto.value.** { *; }
# -keep interface com.google.auto.value.** { *; }

# Rules for TagLib
-keep class com.kyant.taglib.** { *; }

# Rules for JAudioTagger (fallback metadata reader)
-dontwarn org.jaudiotagger.**

# [NUEVO] Regla general para mantener metadatos de Kotlin, puede ayudar a R8
-keep class kotlin.Metadata { *; }

# ExoPlayer FFmpeg extension
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.ffmpeg.** { *; }

# ExoPlayer MIDI extension and JSyn synthesizer
-keep class androidx.media3.decoder.midi.** { *; }
-keep class com.jsyn.** { *; }
-keep class com.softsynth.** { *; }
-dontwarn com.jsyn.**
-dontwarn com.softsynth.**

# Mantener clases de datos y sus miembros para evitar que R8 Full elimine campos
-keepclassmembers class com.theveloper.pixelplay.data.model.** { *; }
-keepclassmembers class com.theveloper.pixelplay.domain.model.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*

# Cast framework classes loaded via manifest/reflective entry points.
-keep class com.theveloper.pixelplay.data.service.cast.CastOptionsProvider { *; }
-keep class * implements com.google.android.gms.cast.framework.OptionsProvider

# Gson generic type capture for backup/restore in release builds.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry { *; }
-keep class com.theveloper.pixelplay.data.backup.model.** { *; }
-keep class com.theveloper.pixelplay.data.backup.module.** { *; }
# Backup payload entities are part of the persisted .pxpl contract.
-keep class com.theveloper.pixelplay.data.database.FavoritesEntity { *; }
-keep class com.theveloper.pixelplay.data.database.SongEngagementEntity { *; }
-keep class com.theveloper.pixelplay.data.database.LyricsEntity { *; }
-keep class com.theveloper.pixelplay.data.database.SearchHistoryEntity { *; }
-keep class com.theveloper.pixelplay.data.database.TransitionRuleEntity { *; }

# Netty channel classes are instantiated reflectively and require public no-arg constructors.
# Without these, release builds can fail with:
# "IllegalArgumentException: Class NioServerSocketChannel does not have a public non-arg constructor"
-keep class io.netty.channel.socket.nio.NioServerSocketChannel { public <init>(); }
-keep class io.netty.channel.socket.nio.NioSocketChannel { public <init>(); }
-keep class io.netty.channel.epoll.EpollServerSocketChannel { public <init>(); }
-keep class io.netty.channel.epoll.EpollSocketChannel { public <init>(); }
-keep class io.netty.channel.kqueue.KQueueServerSocketChannel { public <init>(); }
-keep class io.netty.channel.kqueue.KQueueSocketChannel { public <init>(); }

# Ktor server engine classes (CIO and internals) — prevent R8 from stripping
# service-loaded or reflectively-accessed engine wiring.
-keep class io.ktor.server.engine.** { *; }
-keep class io.ktor.server.cio.** { *; }

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.

# [NUEVO] Reglas para solucionar el error de Ktor y R8
-dontwarn java.lang.management.**
-dontwarn reactor.blockhound.**

-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Image
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.RenderedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.ImageWriter
-dontwarn javax.imageio.stream.ImageInputStream
-dontwarn javax.imageio.stream.ImageOutputStream
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
-dontwarn javax.sound.sampled.AudioFileFormat$Type
-dontwarn javax.sound.sampled.AudioFileFormat
-dontwarn javax.sound.sampled.AudioFormat$Encoding
-dontwarn javax.sound.sampled.AudioFormat
-dontwarn javax.sound.sampled.AudioInputStream
-dontwarn javax.sound.sampled.UnsupportedAudioFileException
-dontwarn javax.sound.sampled.spi.AudioFileReader
-dontwarn javax.sound.sampled.spi.FormatConversionProvider
-dontwarn javax.swing.filechooser.FileFilter

-dontwarn io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.AsyncTask
-dontwarn io.netty.internal.tcnative.Buffer
-dontwarn io.netty.internal.tcnative.CertificateCallback
-dontwarn io.netty.internal.tcnative.CertificateCompressionAlgo
-dontwarn io.netty.internal.tcnative.CertificateVerifier
-dontwarn io.netty.internal.tcnative.Library
-dontwarn io.netty.internal.tcnative.SSL
-dontwarn io.netty.internal.tcnative.SSLContext
-dontwarn io.netty.internal.tcnative.SSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.SSLSessionCache
-dontwarn io.netty.internal.tcnative.SessionTicketKey
-dontwarn io.netty.internal.tcnative.SniHostNameMatcher
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ClientProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$Provider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego

# Echo Extension System Rules
# Keep all interfaces, classes, and sub-packages in dev.brahmkshatriya.echo namespace
# so that dynamic class loaders can load external extension APKs and cast them to these types.
-keep class dev.brahmkshatriya.echo.** { *; }
-keep interface dev.brahmkshatriya.echo.** { *; }
-keepclassmembers class dev.brahmkshatriya.echo.** { *; }
-dontwarn dev.brahmkshatriya.echo.**


# TDLib (Telegram Database Library) rules
# The native libtdjni.so is loaded dynamically at runtime (not bundled in the APK).
# We must keep the Java API classes so TelegramClientManager can call them via reflection.
-keep class org.drinkless.tdlib.** { *; }
-keep interface org.drinkless.tdlib.** { *; }

# ─── Modularization Interface Contracts (Section 3 of Modularization Blueprint) ───────────────
# CRITICAL: These rules prevent R8 from obfuscating or stripping the interfaces and
# contracts that DexClassLoader-loaded modules depend on at runtime. Without these,
# dynamic modules will crash with ClassNotFoundException or NoSuchMethodError.

# 1. Enable obfuscation and allow full optimization while keeping necessary interfaces
# -dontobfuscate (Obfuscation is now ENABLED to shrink APK size)

# 2. Keep the StreamProxy and Registry interfaces so all proxy implementations (current and future
#    dynamically-loaded ones) can be cast to it from the host app.
-keep,allowoptimization interface com.theveloper.pixelplay.data.stream.StreamProxy { *; }
-keep,allowoptimization class com.theveloper.pixelplay.data.stream.CloudStreamProxy { *; }
-keep,allowoptimization class com.theveloper.pixelplay.data.stream.StreamProxyRegistry { *; }

# Keep the Ktor HTTP Server Controller and Proxy Initializer for reflective dynamic classloading at runtime
-keep class com.theveloper.pixelplay.data.service.http.KtorHttpServerController {
    public <init>();
}
-keep class com.theveloper.pixelplay.data.service.http.KtorProxyInitializer {
    public <init>();
}

# 4. Keep shared data model classes so dynamic modules can serialize/deserialize them
-keep,allowoptimization class com.theveloper.pixelplay.data.model.** { *; }

# 5. Allow optimizing Kotlin & Coroutines without renaming (required for dynamic modules
#    that share the same coroutine dispatcher and continuation types)
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
# ──────────────────────────────────────────────────────────────────────────────────────────────

# Ktor & Netty Rules (Crucial for StreamProxy)
# -keep class org.slf4j.** { *; }

# Ktor Specific
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn io.netty.**

# Keep Kotlin reflection if needed by Ktor/Serialization in Release
# -keep class kotlin.reflect.** { *; }

# Kuromoji
-dontwarn com.atilika.kuromoji.**

# Pinyin4J
-dontwarn net.sourceforge.pinyin4j.**

# Glance Widget
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Protobuf (Required by Echo Extensions like Spotify)
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Networking & Serialization (Required by YouTube & other extensions)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okio.**

# HTML Parsing (Required by YouTube/NewPipeExtractor)
-dontwarn org.jsoup.**

# =============================================================================
# TIMBER LOGGING OPTIMIZATION FOR RELEASE BUILDS
# =============================================================================
# Strip VERBOSE and DEBUG log calls entirely from release builds.
# This removes the method calls at bytecode level, eliminating any overhead
# from string concatenation or log message building.

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}

# Also strip Timber.Tree methods used by custom trees (belt and suspenders)
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
}

# Strip Android Log.v and Log.d calls as well
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep desugared JDK library classes for dynamically loaded extensions
-keep class j$.** { *; }
-keep interface j$.** { *; }
-dontwarn j$.**

# Suppress warnings for internal JDK classes referenced by desugared libraries
-dontwarn java.lang.StringCoding**
-dontwarn java.lang.StringLatin1**
-dontwarn java.lang.StringUTF16**
-dontwarn java.util.concurrent.ForkJoinWorkerThread**
-dontwarn jdk.internal.misc.**
-dontwarn sun.nio.fs.**

# Keep login activities from obfuscation
-keep class com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity { *; }
-keep class com.theveloper.pixelplay.presentation.navidrome.auth.NavidromeLoginActivity { *; }
-keep class com.theveloper.pixelplay.presentation.netease.auth.NeteaseLoginActivity { *; }
-keep class com.theveloper.pixelplay.presentation.gdrive.auth.GDriveLoginActivity { *; }
-keep class com.theveloper.pixelplay.presentation.qqmusic.auth.QqMusicLoginActivity { *; }
-keep class com.theveloper.pixelplay.presentation.jellyfin.auth.JellyfinLoginActivity { *; }

