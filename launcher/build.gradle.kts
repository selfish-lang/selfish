val graalvmVersion: String by project

plugins {
    application
    id("org.mikeneck.graalvm-native-image") version "1.2.0"
}

dependencies {
    rootProject.childProjects["language"]?.let { implementation(it) }
    implementation("org.graalvm.sdk:launcher-common:$graalvmVersion")
}

val launcherMainClassName = "fan.zhuyi.selfish.launcher.SelfishLauncher"

tasks.jar {
    manifest {
        attributes(
                "Main-Class" to launcherMainClassName
        )
    }
}

application {
    mainClass.set(launcherMainClassName)
}

nativeImage {
    graalVmHome = if (System.getenv("GRAALVM_HOME") == null) {
        ""
    } else {
        System.getenv("GRAALVM_HOME")
    }
    mainClass = launcherMainClassName
    executableName = "selfish"
    outputDirectory = file("$buildDir/executable")
    dependsOn(":language:jar")
    arguments(
            "--class-path",
            rootProject
                    .childProjects["language"]
                    ?.tasks
                    ?.jar
                    ?.get()
                    ?.archiveFile
                    ?.get()
                    ?.toString(),
            "--macro:truffle",
            "--no-fallback",
            "--initialize-at-build-time"
    )
}