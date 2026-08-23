import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")

extensions.configure<BaseExtension> {
    compileSdkVersion(35)
    defaultConfig {
        minSdk = 26
    }
    packagingOptions {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    jvmToolchain(21)
}
