plugins {
    java
}

allprojects {
    val graalvmVersion: String by project

    group = "fan.zhuyi"
    version = "0.1.0"

    apply {
        plugin("java")
    }

    tasks.compileJava {
        modularity.inferModulePath.set(true)
        options.release.set(11)
    }

    tasks.compileTestJava {
        options.release.set(11)
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.test {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
    }

    repositories {
        jcenter()
    }

    dependencies {
        implementation("org.graalvm.truffle:truffle-api:$graalvmVersion")
        implementation("org.graalvm.sdk:graal-sdk:$graalvmVersion")
        implementation("commons-codec:commons-codec:1.15")
        annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:$graalvmVersion")

        testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs = listOf("--add-exports", "org.graalvm.truffle/com.oracle.truffle.api.nodes=ALL-UNNAMED")
        options.isFork = true // required for above?
        options.forkOptions.executable = "javac" // required for above?
    }

}