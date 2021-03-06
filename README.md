# Selfish

Experimental implementation of Selfish Language using GraalVM.

## Dependency and Setup

- GraalVM (EE or CE, version 21)
- Environment Variable `GRAALVM_HOME` set to install dir of GraalVM

## Basic Build Steps

- To test:
```bash
$ gradle test
```

- To package:
```bash
$ gradle jar
```

- To build nativeImage:
```bash
$ gradle nativeImage
```
