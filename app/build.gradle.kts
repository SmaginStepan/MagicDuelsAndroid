import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The leaderboard server (github.com/SmaginStepan/ScoreServer): its address, this game's id, API key
// and signing secret come from local.properties (not in git), e.g.
//   score.url=https://example.com/lb
//   score.game=arcaneduel
//   score.apiKey=...
//   score.secret=...
// Without them the game still builds; the leaderboard then says it isn't available.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun scoreProp(key: String): String = "\"" + (localProps.getProperty(key) ?: "").replace("\"", "\\\"") + "\""

android {
    namespace = "com.an0obis.magicduel"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.an0obis.magicduel"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SCORE_URL", scoreProp("score.url"))
        buildConfigField("String", "SCORE_GAME", scoreProp("score.game"))
        buildConfigField("String", "SCORE_API_KEY", scoreProp("score.apiKey"))
        buildConfigField("String", "SCORE_SECRET", scoreProp("score.secret"))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
