// GitHub Actions 的 runner 在境外，走官方源更快更稳；本机在国内则优先阿里云镜像。
// 两边都保留备用源，任意一个不通时 Gradle 会自动往下试。
// 注意：pluginManagement 块由 Gradle 单独提前求值，只能用 JDK 自带 API，
// 不能引用脚本里定义的变量，所以这里直接读环境变量。

pluginManagement {
    repositories {
        if (System.getenv("CI") == "true") {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        if (System.getenv("CI") != "true") {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") == "true") {
            google()
            mavenCentral()
        }
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        if (System.getenv("CI") != "true") {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "TvSafetyLock"
include(":app")
