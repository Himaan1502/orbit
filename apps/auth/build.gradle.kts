plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    checkstyle
    id("com.github.spotbugs") version "6.0.7"
    jacoco
}

group = "com.orbit"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Self-hosted OIDC provider — see ADR-004.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = "10.12.7"
    configFile = file("checkstyle/checkstyle.xml")
    isIgnoreFailures = true
}

spotbugs {
    ignoreFailures.set(true)
    excludeFilter.set(file("spotbugs/exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    reports {
        create("html") {
            required.set(true)
        }
        create("xml") {
            required.set(false)
        }
    }
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
