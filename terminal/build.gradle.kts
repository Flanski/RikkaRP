plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "me.rerere.terminal"

    // RikkaRP: 官方 NDK 只有 x86_64 主机版，本机 aarch64 无法执行 CMake；
    // 改用 Termux clang 预编译的 jniLibs/arm64-v8a/libtermux.so（源码与上游逐字节一致）。
    // 如需恢复 CMake：安装 NDK 后加回 externalNativeBuild，并删除该 jniLibs 目录。

    testOptions {
        // 上游单测会碰到 android.util.Base64 等 Android 类, 沿用上游的设置让它们返回默认值
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.junit)
}
