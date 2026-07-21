plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")

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
    mainClass = "io.github.agorokh.scenariocraft.renderer.RendererCli"
    applicationName = "renderer"
}

tasks.test {
    useJUnitPlatform()
    systemProperty("scenariocraft.repoRoot", rootProject.projectDir.absolutePath)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

val renderGolden = tasks.register<JavaExec>("renderGolden") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "--in", rootProject.file("src/test/resources/fixtures/small-house.voxels.json"),
        "--out", layout.buildDirectory.dir("golden-render").get().asFile
    )
}

tasks.register<Copy>("regenerateGolden") {
    group = "verification"
    description = "Regenerates the committed small-house golden isometric image."
    dependsOn(renderGolden)
    from(layout.buildDirectory.file("golden-render/iso-ne.png"))
    into(rootProject.file("src/test/resources/golden"))
    rename { "small-house-iso-ne.png" }
}

val renderShowcaseVolcano = tasks.register<JavaExec>("renderShowcaseVolcano") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "--in", rootProject.file("src/test/resources/fixtures/speed-build-rainbow-volcano.voxels.json"),
        "--out", layout.buildDirectory.dir("showcase-volcano").get().asFile
    )
}

val renderShowcaseWalls = tasks.register<JavaExec>("renderShowcaseWalls") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "--in", rootProject.file("src/test/resources/fixtures/speed-build-rainbow-volcano-walled.voxels.json"),
        "--out", layout.buildDirectory.dir("showcase-volcano-walled").get().asFile
    )
}

tasks.register<Copy>("regenerateShowcase") {
    group = "verification"
    description = "Regenerates the four committed rainbow-volcano showcase images."
    dependsOn(renderShowcaseVolcano, renderShowcaseWalls)
    from(layout.buildDirectory.file("showcase-volcano/iso-ne.png")) {
        rename { "rainbow-volcano-iso.png" }
    }
    from(layout.buildDirectory.file("showcase-volcano/plan.png")) {
        rename { "rainbow-volcano-gallery.png" }
    }
    from(layout.buildDirectory.file("showcase-volcano-walled/iso-ne.png")) {
        rename { "rainbow-volcano-walls.png" }
    }
    from(layout.buildDirectory.file("showcase-volcano-walled/cut-z.png")) {
        rename { "rainbow-volcano-cutaway.png" }
    }
    into(rootProject.file("site/assets/scenes"))
}
