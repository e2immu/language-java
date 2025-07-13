/*
 * Copyright (c) 2022-2023, CodeLaser BV, Belgium.
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */

plugins {
    `java-library`
    `maven-publish`
}

group = "org.e2immu"


repositories {
    maven {
        url = uri(project.findProperty("codeartifactPublicUri") as String)
        credentials {
            username = "aws"
            password = project.findProperty("codeartifactToken") as String
        }
    }
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

val slf4jVersion = project.findProperty("slf4jVersion") as String
val jupiterApiVersion = project.findProperty("jupiterApiVersion") as String
val jupiterEngineVersion = project.findProperty("jupiterEngineVersion") as String
val logbackClassicVersion = project.findProperty("logbackClassicVersion") as String

dependencies {
    api("org.e2immu:e2immu-cst-api:$version")
    api("org.e2immu:e2immu-inspection-api:$version")
    api("org.e2immu:e2immu-external-support:$version")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.ow2.asm:asm:9.7.1")

    testImplementation("org.e2immu:e2immu-cst-impl:$version")
    testImplementation("org.e2immu:e2immu-inspection-resource:$version")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterApiVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterEngineVersion")
    testRuntimeOnly("ch.qos.logback:logback-classic:$logbackClassicVersion")
}

tasks.test {
    useJUnitPlatform()
}


publishing {
    repositories {
        maven {
            url = uri(project.findProperty("publishPublicUri") as String)
            credentials {
                username = project.findProperty("publishUsername") as String
                password = project.findProperty("publishPassword") as String
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "language-java-bytecode of e2immu analyser"
                description = "Static code analyser focusing on modification and immutability. " +
                        "This module provides support for parsing JVM bytecode."
                url = "https://e2immu.org"
                scm {
                    url = "https://github.com/e2immu"
                }
                licenses {
                    license {
                        name = "GNU Lesser General Public License, version 3.0"
                        url = "https://www.gnu.org/licenses/lgpl-3.0.html"
                    }
                }
                developers {
                    developer {
                        id = "bnaudts"
                        name = "Bart Naudts"
                        email = "bart.naudts@e2immu.org"
                    }
                }
            }
        }
    }
}