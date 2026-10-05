plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "moe.yzf.comic.wear"
    // Android 37 起平台按「主.次」版本发布（已装 platforms/android-37.1），
    // 因此必须同时给出 compileSdkMinor，AGP 才能定位到 android-37.1。
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "moe.yzf.comic.wear"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/INDEX.LIST",
        )
    }

    lint {
        abortOnError = false
    }

    testOptions {
        // JVM 单测里 OkHttp 会调用 android.util.Log；返回默认值即可，
        // 否则会打印 "Method isLoggable in android.util.Log not mocked"。
        unitTests.isReturnDefaultValues = true
        // Robolectric 需要读取真实资源与合并后的清单。
        unitTests.isIncludeAndroidResources = true
    }
}

// 内置 Kotlin 下 jvmTarget 默认跟随 android.compileOptions.targetCompatibility，
// 上面已设为 17，无需再显式声明 kotlin { compilerOptions { ... } }。

// Robolectric 通过反射访问 JDK 内部 API（FileDescriptor / SharedSecrets 等），
// 而 JDK 17+ 的模块封装默认拒绝，报：
//   IllegalAccessException: ... cannot access class jdk.internal.access.SharedSecrets
//      (in module java.base) because module java.base does not export jdk.internal.access
// 这几个开关是让 Robolectric 在 JDK 25 上跑起来的必要前提。
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 协议契约测试跑在 JVM 上，不需要真机/模拟器。
    testImplementation(libs.junit)

    // Robolectric：在 JVM 上跑真实的 Android 运行时（Context / 文件 / DataStore /
    // Application / Activity / Compose 组合），用来验证「装到手表上会不会崩」。
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
