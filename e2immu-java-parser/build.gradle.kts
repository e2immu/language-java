/*
 * Copyright (c) 2022-2023, CodeLaser BV, Belgium.
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */

plugins {
    java
    `maven-publish`
}

group = "org.e2immu"


repositories {
    maven {
        url = uri(project.findProperty("codeartifactUri") as String)
        credentials {
            username = "aws"
            password = project.findProperty("codeartifactToken") as String
        }
    }
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

dependencies {
    implementation("org.e2immu:e2immu-internal-util:${version}")
    implementation("org.e2immu:e2immu-cst-api:${version}")
    implementation("org.e2immu:e2immu-external-support:${version}")
    implementation("org.e2immu:e2immu-inspection-api:${version}")
    implementation("org.slf4j:slf4j-api:2.0.7")

    testImplementation("org.jetbrains:annotations:24.1.0")
    testImplementation("org.e2immu:e2immu-cst-impl:${version}")
    testImplementation("org.e2immu:e2immu-inspection-parser:${version}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.8")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            url = uri(project.findProperty("publishUri") as String)
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
                name = "language-java-parser of e2immu analyser"
                description = "Static code analyser focusing on modification and immutability. " +
                        "This module provides support for parsing Java sources using CongoCC."
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