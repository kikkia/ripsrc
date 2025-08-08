plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

base {
    archivesName = "ripsrc"
}

java {
    withJavadocJar()
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api("com.github.topi314.lavasrc:lavasrc:2d5cd1c")
    compileOnly("dev.arbjerg:lavaplayer:2.0.4")
    implementation("commons-io:commons-io:2.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlin:kotlin-annotations-jvm:1.9.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    compileOnly("org.slf4j:slf4j-api:2.0.7")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                artifactId = base.archivesName.get()
                from(components["java"])
            }
        }
    }
}

kotlin {
    jvmToolchain(11)
}