plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":renderer"))
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.snakeyaml:snakeyaml-engine:3.0.1")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "io.github.agorokh.scenariocraft.judge.JudgeCli"
    applicationName = "judge"
}

tasks.test {
    useJUnitPlatform()
    systemProperty("scenariocraft.repoRoot", rootProject.projectDir.absolutePath)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.named("build") {
    dependsOn("installDist")
}
