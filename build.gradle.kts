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
        implementation("commons-codec:commons-codec:1.15")
        annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:$graalvmVersion")

        testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
    }

}