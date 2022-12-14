import me.champeau.jmh.JMHTask
import me.champeau.jmh.JmhBytecodeGeneratorTask
import org.gradle.internal.impldep.org.apache.commons.io.output.*
import org.gradle.internal.impldep.org.eclipse.jgit.lib.ObjectChecker.type

plugins {
    id("java")
    id("application")
    id("me.champeau.jmh") version "0.6.8"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("blackScholes.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.jetbrains:annotations:20.1.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.openjdk.jmh:jmh-core:1.35")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:1.35")
}

tasks {
    val ENABLE_PREVIEW = "--enable-preview"
    withType<JavaCompile> {
        options.compilerArgs.add(ENABLE_PREVIEW)
        options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
    }
    withType<CreateStartScripts> {
        applicationName = "javaPerf"
        if (project.hasProperty("debug")) {
            defaultJvmOpts = listOf("--enable-preview", "--add-modules", "jdk.incubator.vector",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:CompileThreshold=100", "-XX:InlineSmallCode=100",
                "-XX:+PrintCompilation",  "-XX:+PrintInlining", "-XX:+PrintIntrinsics",
                "-XX:+PrintAssembly","-XX:PrintAssemblyOptions=intel",
                "-Xmx16000M", "-XX:NewSize=8G")
        }
        else{
            defaultJvmOpts = listOf("--enable-preview", "--add-modules", "jdk.incubator.vector","-Xmx16000M", "-XX:NewSize=8G",
                "-XX:+UnlockDiagnosticVMOptions")
        }
    }
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE //problem seems to arise with the JMH plugin without this
        manifest {
            attributes["Main-Class"] = application.mainClass
        }
        from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        jvmArgs = listOf("--enable-preview", "--add-modules", "jdk.incubator.vector")
    }
    withType<JavaExec> {
        if (project.hasProperty("debug")) {
            jvmArgs = listOf("--enable-preview", "--add-modules", "jdk.incubator.vector",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:CompileThreshold=50","-XX:InlineSmallCode=100",
                "-XX:+PrintCompilation",  "-XX:+PrintInlining", "-XX:+PrintIntrinsics",
                "-XX:+PrintAssembly","-XX:PrintAssemblyOptions=intel",
                "-Xmx16000M","-XX:NewSize=8G")
        }
        else{
            jvmArgs = listOf("--enable-preview", "--add-modules", "jdk.incubator.vector","-Xmx16000M","-XX:NewSize=8G",
                "-XX:+UnlockDiagnosticVMOptions")
        }

    }
    withType<JmhBytecodeGeneratorTask> {
        jvmArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector","-Xmx16000M", "-XX:NewSize=8G",
            "-XX:+UnlockDiagnosticVMOptions"))
    }
    withType<JMHTask> {
        jvmArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector","-Xmx16000M", "-XX:NewSize=8G",
            "-XX:+UnlockDiagnosticVMOptions"))
        profilers.addAll(listOf("perf","stack", "gc") )// includes the linux perf profiler for ipc and cache miss info
        warmupIterations.set(5)
        iterations.set(5)
        fork.set(5)
        resultFormat.set("csv")
//        logging.captureStandardOutput(LogLevel.LIFECYCLE)
//        val standardOutput = ByteArrayOutputStream()
//        val buildPath
//        commandLine("echo",standardOutput, "")
//        val standardOutput = new LogOutputStream(logger, LogLevel.INFO)
//        val errorOutput =   LogOutputStream(logger, LogLevel.ERROR)

//        doFirst {
//            println("This message should always be displayed.")
//        }
    }
}
// add a way to capture stdout for jmh
