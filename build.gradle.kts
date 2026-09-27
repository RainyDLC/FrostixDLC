plugins {
    id("fabric-loom") version "1.18.2"
    id("maven-publish")
}

val minecraftVersion = (findProperty("minecraft_version") as String?) ?: "1.21.11"
val yarnMappings = (findProperty("yarn_mappings") as String?) ?: "1.21.11+build.6:v2"
val loaderVersion = (findProperty("loader_version") as String?) ?: "0.19.5"
val fabricVersion = (findProperty("fabric_version") as String?) ?: "0.141.6+1.21.11"

version = (findProperty("mod_version") as String?) ?: "1.5-privacy"
group = (findProperty("maven_group") as String?) ?: "rtx.kimiko"

base {
    archivesName.set((findProperty("archives_base_name") as String?) ?: "kimiko-privacy")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings")

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.12+kotlin.2.4.0")
    include("net.fabricmc:fabric-language-kotlin:1.13.12+kotlin.2.4.0")

    // Local libraries bundled in libs/
    implementation(fileTree("libs") {
        include("*.jar")
        exclude("geckolib*", "yarn*")
    })
    modImplementation(files("libs/geckolib-fabric-1.21.11-5.4.5.jar"))

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

loom {
    accessWidenerPath.set(file("src/main/resources/kimiko.accesswidener"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}
