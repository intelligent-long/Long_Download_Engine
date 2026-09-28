plugins {
    id("com.android.library")
    `maven-publish`
}

group = "com.longx.intelligent.lib.longdownloadengine.android"
version = "1.0"
val artifact = "longdownloadengine-android"
val manualBuildTime = "2026 年 9 月 27 日"

val generatedSourcesDir = layout.projectDirectory.dir("src/main/java")

val generateBuildInfoTask = tasks.register("generateBuildInfo") {
    inputs.property("version", project.version.toString())
    inputs.property("buildTime", manualBuildTime)
    outputs.dir(generatedSourcesDir)
    doLast {
        val outDir = generatedSourcesDir.asFile
        val packageDir = File(outDir, "com/longx/intelligent/lib/longdownloadengine/android/_build")
        if (!packageDir.exists()) {
            packageDir.mkdirs()
        }
        File(packageDir, "BuildInfo.java").writeText("""
            package com.longx.intelligent.lib.longdownloadengine.android._build;

            public class BuildInfo {
                public static final String VERSION = "${project.version}";
                public static final String BUILD_TIME = "$manualBuildTime";
            }
        """.trimIndent())
    }
}

tasks.named("preBuild") {
    dependsOn(generateBuildInfoTask)
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.0")
        }
    }
}

android {
    namespace = "com.longx.intelligent.lib.longdownloadengine.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = group.toString()
                artifactId = artifact
                version = version.toString()
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    implementation(project(":longdownloadengine-core"))
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
}