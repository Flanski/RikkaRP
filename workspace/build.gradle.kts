plugins {
    id("rikkahub.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.workspace"

    defaultConfig {
        // RikkaRP: 官方 NDK 只有 x86_64 主机版本，aarch64 上无法执行；
        // 且完整解压需 3GB+。改为用 Termux 自带 clang 预编译 libtermux.so 放进
        // jniLibs/arm64-v8a/，因此这里不再调用 CMake。
    }
    // RikkaRP: externalNativeBuild 已移除，改用 jniLibs 中的预编译 libtermux.so。
    // 如需恢复 CMake，请同时安装 NDK 并删掉 jniLibs/arm64-v8a/libtermux.so。
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xz)
    implementation(libs.jsch)
    // Android 不认 multi-release jar, JSch 的 Ed25519 / Curve25519 等算法只能走 BouncyCastle 实现
    implementation(libs.bouncycastle.bcprov)
    testImplementation(libs.junit)
    // 单测里起一个嵌入式 SSH/SFTP 服务器验证 SshWorkspaceBackend
    testImplementation(libs.apache.sshd.core)
    testImplementation(libs.apache.sshd.sftp)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
