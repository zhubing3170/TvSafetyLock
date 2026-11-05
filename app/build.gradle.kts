plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** 读 -P 参数或 gradle.properties；GitHub Actions 打包时靠这个注入配置 */
fun gradleProp(name: String, fallback: String): String =
    (project.findProperty(name) as? String)?.takeIf { it.isNotBlank() } ?: fallback

/** 把任意文本转成合法的 Java 字符串字面量（密钥里可能含引号或反斜杠） */
fun javaLiteral(s: String): String = "\"" + s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n") + "\""

/**
 * 激活码密钥：绝不写进源码（仓库是 Public 的）。
 * 优先级：命令行 -PLICENSE_SECRET → 环境变量 LICENSE_SECRET → 空串。
 * 空串时 App 会退回演示密钥，仅用于自己测试，正式发版必须配。
 * GitHub Actions 从仓库 Secret 注入，本地打包用 -P 传参。
 */
val licenseSecret: String = gradleProp("LICENSE_SECRET", System.getenv("LICENSE_SECRET") ?: "").trim()

/**
 * 在线题库地址。
 * - GitHub Actions：workflow 用 -PquestionBankUrl 自动填成当前仓库的 raw 地址
 * - 本地打包：在根目录 gradle.properties 里填
 * - 留空：App 只用内置题库，设置页会提示「未配置」
 */
val questionBankUrl: String = gradleProp("questionBankUrl", "").trim()

/**
 * 备用题库地址（jsDelivr CDN）。国内电视访问 raw.githubusercontent.com 经常不通，
 * App 会先试主地址、失败再自动试这个，任一返回合法题库就算成功。
 */
val questionBankUrlMirror: String = gradleProp("questionBankUrlMirror", "").trim()

android {
    namespace = "com.tvsafelock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvsafelock.app"
        minSdk = 23          // 兼容安卓 6.0 以上的老电视盒子
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        // 写死在包里：运行时只访问这两个地址，家长不用在电视上输一长串 URL
        buildConfigField("String", "QUESTION_BANK_URL", javaLiteral(questionBankUrl))
        buildConfigField("String", "QUESTION_BANK_URL_MIRROR", javaLiteral(questionBankUrlMirror))
        // 激活码密钥由打包环境注入，源码里不出现
        buildConfigField("String", "LICENSE_SECRET", javaLiteral(licenseSecret))
    }

    // 正式版签名：把 my-release-key.jks 放到 app/ 目录即可自动启用。
    // 密码优先读 -P 参数（GitHub Actions 用 Secrets 传），本地直接在下面改也行。
    // 没放 keystore 的时候自动退回 debug 签名，保证能先跑起来看效果。
    signingConfigs {
        create("release") {
            val ks = file("my-release-key.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = gradleProp("KEYSTORE_PASSWORD", "改成你的密码")
                keyAlias = gradleProp("KEY_ALIAS", "tvsafelock")
                keyPassword = gradleProp("KEY_PASSWORD", "改成你的密码")
            }
        }
    }

    buildTypes {
        release {
            // 关闭混淆，方便你后续自己改代码；要减小体积可改 true
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (file("my-release-key.jks").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    // 在线题库下载放在 IO 线程，显式声明避免依赖传递版本漂移
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
