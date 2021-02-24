sourceSets {

    main {
        withConvention(ScalaSourceSet::class) {
            scala {
                setSrcDirs(listOf("src/main/scala"))
            }
        }
        java {
            setSrcDirs(listOf("src/main/java"))
        }
    }
    test {
        withConvention(ScalaSourceSet::class) {
            scala {
                setSrcDirs(listOf("src/test/scala"))
            }
        }
        java {
            setSrcDirs(listOf("src/test/java"))
        }
    }
}