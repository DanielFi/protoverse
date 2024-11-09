plugins {
    id("java")
}

group = "danielf"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.smali:dexlib2:2.5.2")
}

tasks.test {
    useJUnitPlatform()
}