import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestExecutable
plugins {
     `cpp-application`
     `cpp-unit-test`
     `visual-studio`
}
unitTest {
    source {
        source.setFrom("src/test/cpp", "src/main/headers/blackScholes.h")

    }

    privateHeaders {
        from("externalLibs/eigen-3.4.0/", "externalLibs/version2-2.02.00/", "src/main/headers/")
    }
    headerFiles.files.add(file("src/main/headers/blackScholes.h"))
//    print(headerFiles.asPath)
//    binaries.configureEach {
//        compileTask.get().compilerArgs.add("-I/home/shiraj/Projects/performanceTesting/cppPerf/src/main/headers")
//        linkLibraries.files.add(file("/home/shiraj/Projects/performanceTesting/cppPerf/src/main/headers/blackScholes.h"))
//    }

}

val fmtHeaders = file("version2-2.02.00/")

application {
    targetMachines.add(machines.linux.x86_64)
    privateHeaders {
        from("eigen-3.4.0/", "version2-2.02.00/")
    }
}

components.withType(DefaultCppTestExecutable::class.java){
//    linkTask.get().linkerArgs.add("-I/home/shiraj/Projects/performanceTesting/cppPerf/main/headers/")
    linkTask.get().libs.setFrom("/home/shiraj/Projects/performanceTesting/cppPerf/externalLibs/svml_files/libsvml.a",
        "/home/shiraj/Projects/performanceTesting/cppPerf/externalLibs/svml_files/libirc.a")
//        "/home/shiraj/Projects/performanceTesting/cppPerf/version2-2.02.00/vectormath_lib.h",
//        "/home/shiraj/Projects/performanceTesting/cppPerf/src/main/headers/blackScholes.h")
//    linkTask.get().source.setFrom("/home/shiraj/Projects/performanceTesting/cppPerf/version2-2.02.00/vectormath_lib.h")
}


tasks.withType(CppCompile::class.java).configureEach {
    compilerArgs.add("-v")
    compilerArgs.add("-std=c++17")
    compilerArgs.add("-fPIC")
//    compilerArgs.add("-fno-stack-protector") //not sure made a difference
    includes {
        "/home/shiraj/Projects/performanceTesting/cppPerf/externalLibs/svml_files"
    }
    includes {
        "/home/shiraj/Projects/performanceTesting/cppPerf/src/main/headers"
    }

//    compilerArgs.add("-L/home/shiraj/Projects/performanceTesting/cppPerf/svml_files/ -llibirc -llibsvml")
//    compilerArgs.add("-I/home/shiraj/Projects/performanceTesting/cppPerf/svml_files/")
//    systemIncludes.setFrom( "/home/shiraj/Projects/performanceTesting/cppPerf/svml_files")
}

tasks.withType(LinkExecutable::class.java).configureEach {
    linkerArgs.add("-v")
    linkerArgs.add("-std=c++17")
    linkerArgs.add("-fPIC")
    //below libs is for linkDebug
    libs.setFrom("/home/shiraj/Projects/performanceTesting/cppPerf/externalLibs/svml_files/libsvml.a",
        "/home/shiraj/Projects/performanceTesting/cppPerf/externalLibs/svml_files/libirc.a")
}

//version = "1.2.1"