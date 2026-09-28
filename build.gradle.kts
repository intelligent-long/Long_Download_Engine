plugins {
    id("com.android.library") version "8.7.0" apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        enabled = false
    }
}