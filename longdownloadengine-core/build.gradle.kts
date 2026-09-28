plugins {
    id("java-library")
    `maven-publish`
}

group = "com.longx.intelligent.lib.longdownloadengine"
version = "1.0"
val artifact = "longdownloadengine-core"
val buildTime = "2026 年 9 月 27 日"

val generatedSourcesDir = layout.projectDirectory.dir("src/main/java")

val generateBuildInfoTask = tasks.register("generateBuildInfo") {
    inputs.property("version", project.version.toString())
    inputs.property("buildTime", buildTime)
    outputs.dir(generatedSourcesDir)
    doLast {
        val outDir = generatedSourcesDir.asFile
        val packageDir = File(outDir, "com/longx/intelligent/lib/longdownloadengine/core/_build")
        if (!packageDir.exists()) {
            packageDir.mkdirs()
        }
        File(packageDir, "BuildInfo.java").writeText("""
            package com.longx.intelligent.lib.longdownloadengine.core._build;

            public class BuildInfo {
                public static final String VERSION = "${project.version}";
                public static final String BUILD_TIME = "$buildTime";
            }
        """.trimIndent())
    }
}

tasks.named("compileJava") {
    dependsOn(generateBuildInfoTask)
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
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
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.4.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
}