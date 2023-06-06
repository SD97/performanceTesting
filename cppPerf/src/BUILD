
#### 
# The following two use cc_import as the svml libraries we obtained from the intel compiler are precompiled C libs
# They will be used by the blackScholesFunctions library
####
cc_import(
  name = "svmllib",
#  hdrs = ["mylib.h"],
  static_library = "externalLibs/svml_files/libsvml.a"
  # If alwayslink is turned on,
  # libmylib.a will be forcely linked into any binary that depends on it.
  # alwayslink = 1,
)

cc_import(
  name = "svmlirc",
#  hdrs = ["mylib.h"],
  static_library = "externalLibs/svml_files/libirc.a"
  # If alwayslink is turned on,
  # libmylib.a will be forcely linked into any binary that depends on it.
  # alwayslink = 1,
)

#### 
# The actual functions are stored in a library, so we can call them from different binaries
####
cc_library(
  name = "blackScholesFunctions",
  srcs = (glob(["main/**", "externalLibs/version2-2.02.00/*.h","main/headers/**"],
                exclude=["main/cpp/Eigen/**","main/cpp/main.cpp"])),
  # hdrs =["main/headers"],
  includes =["externalLibs/version2-2.02.00/","main/headers/"],
  linkopts = ["-fPIC", "-std=c++17"], #"-v"
  deps = [":svmllib", ":svmlirc"],
  # linkstatic = True,
)

#### 
# A Binary that just calculates the black scholes formula for some pre-determined input 
####

cc_binary(
    name = "blackScholes",
    # srcs = ["main/cpp/main.cpp","main/headers/blackScholes.h"],
    srcs = (glob(["main/cpp/main.cpp", "externalLibs/version2-2.02.00/*.h","main/headers/**"],
              exclude=["main/cpp/Eigen/**"])),
    includes =[":blackScholesFunctions","externalLibs/version2-2.02.00/","main/headers/"],
    linkopts = ["-fPIC", "-std=c++17", "-g"], #"-v"
    deps = [":svmllib", ":svmlirc"],
)

####
# test build
####
cc_test(
    name = "test",
    srcs =  (glob(["test/cpp/testBlackScholes.cpp", "externalLibs/version2-2.02.00/*.h","main/headers/**"])),
    includes = ["externalLibs/version2-2.02.00/","main/headers/"],
    linkopts = ["-fPIC", "-std=c++17", "-g"], #"-v"
    deps = ["@com_google_googletest//:gtest_main",":svmllib", ":svmlirc"],
)

#### 
# A Binary that does performanceTest
####
cc_binary(
  name = "performanceTest",
  # size = "small",
  srcs = ["performanceTests/cpp/performanceTest.cpp"],
  deps = ["@com_google_googletest//:gtest_main", "@com_google_benchmark//:benchmark",":blackScholesFunctions"],
  linkopts = ["-fPIC", "-std=c++17", "-fconcepts"]

)