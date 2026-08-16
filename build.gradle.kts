plugins {
    java
    application
}

group = "com.warframemarket"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.1")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Compiled against Java 21 so the build works on any JDK 21+ (Windows or Linux)
// without requiring a toolchain download.
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

application {
    mainClass = "com.warframemarket.collector.Main"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

/**
 * Self-contained runnable jar (application classes + Jackson) that only needs a JRE 21+.
 *   java -jar build/libs/warframe-market-collector-<version>-all.jar
 */
val fatJar = tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Builds a single runnable jar containing all runtime dependencies."
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to "Warframe Market Collector",
            "Implementation-Version" to project.version.toString(),
        )
    }

    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.map { cp -> cp.map { if (it.isDirectory) it else zipTree(it) } })

    // Dependency jars ship signatures that become invalid once merged.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
}

tasks.named("assemble") { dependsOn(fatJar) }

/**
 * Native, self-contained application image via the JDK's jpackage.
 * Produces a native launcher for whatever OS the build runs on:
 *   Windows -> build/jpackage/WarframeMarketCollector/WarframeMarketCollector.exe
 *   Linux   -> build/jpackage/WarframeMarketCollector/bin/WarframeMarketCollector
 *
 * Override the package type to build an installer instead (needs extra tooling:
 * WiX on Windows, dpkg/rpm-build on Linux):
 *   ./gradlew jpackageImage -PpackageType=exe
 */
tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Builds a native application image with jpackage."
    dependsOn(fatJar)

    val javaHome = System.getProperty("java.home")
    val jpackage = File(javaHome, if (System.getProperty("os.name").startsWith("Windows")) "bin/jpackage.exe" else "bin/jpackage")
    val packageType = (project.findProperty("packageType") as String?) ?: "app-image"
    val inputDir = layout.buildDirectory.dir("libs").get().asFile
    val outputDir = layout.buildDirectory.dir("jpackage").get().asFile

    doFirst {
        if (!jpackage.exists()) {
            throw GradleException("jpackage not found at $jpackage - build with a full JDK (not a JRE).")
        }
        outputDir.mkdirs()
        // jpackage refuses to overwrite an existing image.
        File(outputDir, "WarframeMarketCollector").deleteRecursively()
    }

    commandLine(
        jpackage.absolutePath,
        "--type", packageType,
        "--name", "WarframeMarketCollector",
        "--app-version", project.version.toString(),
        "--vendor", "Warframe Market Collector",
        "--description", "Collects and displays warframe.market prices for mods and prime items",
        "--input", inputDir.absolutePath,
        "--main-jar", "${project.name}-${project.version}-all.jar",
        "--main-class", application.mainClass.get(),
        "--dest", outputDir.absolutePath,
        "--java-options", "-Xmx512m",
    )
}
