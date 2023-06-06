
# C++ Performance Testing

## Introduction
- The cpp project within this repoistory deals with vectorized and scalar implementations of the Black Scholes formula, specifically the Black 76 formula, minus dividends because I don't believe in dividend investing
- It's similar to the java version I wrote, but with several differences in vectorization libraries used, formula implementation etc.  (e.g. the cumulative normal distribution is written much more cleanly, as it directly uses the error functio)
- In C++, there are several ways to achieve vectorization.
    - Autovectorization by the compiler
    - SIMD Instrinsics
    - GCC Vector Extensions (act as wrappers of the above)
    - Agnar Fog's vector library (VCL), xsimd and other small intrinsic wrappers
    - Library like Eigen, or blaze (Blaze might be the above...)
    - Finally, to add extra functionality you have Intel's MKL library / Intel's OneAPI (these are mathmatical backend libaries which provide vectorized and optimized transcendental functions. Intel's MKL is an implementation of BLAS with 3 levels of suport)
- We went with Agnar Fog's VCL library, which is essentially a wrapper around the intrinsics and intel's math library
- These "small" libraries are good for custom pievces of SIMD code, but a library like Eigen does well for very long vectors/ large matrices (huge method entry/exit)
- Source: https://news.ycombinator.com/item?id=30906376 
- Essentially, as you need to load numbers into your vector data type, with a huge array of numbers, more cpu 
time becomes dominated by this fact, vs the actual vectorized functions. 
- Also, for this example, all we really need is a vectorized error function not really matrix x matrix ops
- Thus, something like Eigen can do this efficiently, I believe, but for like smaller examples not so
- Re: math libraries like Intel's Math API, as recently as a few years ago, it had subpar performance on AMD CPU's. (many sources)
- As of 2022, perhaps it is better?
- https://www.agner.org/optimize/blog/read.php?i=49#1041, https://www.agner.org/forum/viewtopic.php?f=1&t=6#p216 
and 
https://www.agner.org/forum/viewtopic.php?f=1&t=6#p216 
- That being said, there is OpenBLAS, and AMD's version of MKL, but I'm not sure OpenBLAS has the erfc() we need. Also, OpenMP, but again, not sure if there's the erfc() 

## Commands
### Build Commands
- From: ~/Projects/performanceTesting/cppPerf/src
- bazel build //src:blackScholes --verbose_failures --strip=never
- bazel build  --compilation_mode=opt //src:performanceTest --verbose_failures --define pfm=1
- bazel build //src:test --verbose_failures --strip=never
### Run
- With: bazel-bin/src/blackScholes
### Debug
- With: gdb ./bazel-bin/src/blackScholes
### Test 
- With:  bazel-bin/src/test
### Perf Test Commands
- Run with: bazel-bin/src/performanceTest
  bazel-bin/src/performanceTest --benchmark_perf_counters=CACHE-MISSES,BRANCH-MISSES
- bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS
- pause/resume dont work well for some reason with counters! 
- bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,cache-misses
- bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,branch-misses
- bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,stalled-cycles-backend
- bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,L1-dcache-loads

## Setup
1) Agnar Fog's VCL library + Intel's SIMD library from OneAPI (2023.01 version)
    - Instructions to get Intel's OneAPI files:
2) Utilizing AMD's uprof profiler 
- In order to get the above working I had to do quite a lot:
   1) BPF is not supported on this system. Please check the Linux kernel version
   Please install the BCC library to support the OS Tracing
   2) Then:
      sudo dpkg --install amduprof_4.0-341_amd64.deb
   3) Then: Error:
      shiraj@JOTARO:~$  sudo dpkg --install amduprof_4.0-341_amd64.deb
      Selecting previously unselected package amduprof.
      (Reading database ... 203333 files and directories currently installed.)
      Preparing to unpack amduprof_4.0-341_amd64.deb ...
      Unpacking amduprof (4.0-341) ...
      Setting up amduprof (4.0-341) ...
      ERROR: Linux headers is required for installing AMD Power Profiler driver.
      Please install the sources using
      sudo apt-get install linux-headers-5.15.90.1-microsoft-standard-WSL2
      and then start the installation again.
   [OS-Trace Info]
   BPF Supported        : No
   BCC Installed        : No
   OS Tracing Supported : No
   BPF is not supported on this system. Please check the Linux kernel version
   Please install the BCC library to support the OS Tracing
   4) Current system info
      shiraj@JOTARO:~$ uname -a
      Linux JOTARO 5.15.90.1-microsoft-standard-WSL2 #1 SMP Fri Jan 27 02:56:13 UTC 2023 x86_64 x86_64 x86_64 GNU/Linux
      shiraj@JOTARO:~$ llsb_release -a^C
      shiraj@JOTARO:~$ lsb_release -a
      No LSB modules are available.
      Distributor ID: Ubuntu
      Description:    Ubuntu 20.04.5 LTS
      Release:        20.04
      Codename:       focal
   5) pre build:
      sudo apt-get install flex bison libssl-dev libelf-dev dwarves
      and --fix-missing if necessary too
   5) build kernel using:
      https://stackoverflow.com/questions/60237123/is-there-any-method-to-run-perf-under-wsl
BUT, once you do that, also run:
   cp Microsoft/config-wsl .config
   make oldconfig && make prepare
   make scripts
   make modules
   sudo make modules_install
   https://github.com/iovisor/bcc/blob/master/INSTALL.md#wslwindows-subsystem-for-linux---binary
   https://unix.stackexchange.com/questions/594470/wsl-2-does-not-have-lib-modules (part 1, compiling kernel, similar instr here)
6) install it:
   kernel=\\wsl.localhost\Ubuntu\home\shiraj\WSL2-Linux-Kernel
7) Restart wsl
8) try the thing again, I still got error for the power package, but that's for energy monitoring, sources
   the actual application works

## Results
2023-06-03T21:32:30-04:00
Running bazel-bin/src/performanceTest
Run on (16 X 3800.05 MHz CPU s)
CPU Caches:
  L1 Data 32 KiB (x8)
  L1 Instruction 32 KiB (x8)
  L2 Unified 512 KiB (x8)
  L3 Unified 32768 KiB (x1)
Load Average: 0.01, 0.05, 0.07

```
bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,branch-misses
--------------------------------------------------------------------------------------
Benchmark                            Time             CPU   Iterations UserCounters...
--------------------------------------------------------------------------------------
BM_blackScholesVector/1280       11313 ns        11313 ns        61059 CYCLES=52.1403k INSTRUCTIONS=123.327k branch-misses=2.12637
BM_blackScholesVector/4096       44367 ns        44366 ns        15804 CYCLES=176.333k INSTRUCTIONS=389.805k branch-misses=20.459
BM_blackScholesVector/25600     350435 ns       350429 ns         1996 CYCLES=1.21629M INSTRUCTIONS=2.423M branch-misses=282.684
BM_blackScholesScalar/1280       25668 ns        25663 ns        27219 CYCLES=117.769k INSTRUCTIONS=349.201k branch-misses=2.26199
BM_blackScholesScalar/4096       81575 ns        81571 ns         8415 CYCLES=373.874k INSTRUCTIONS=1.11241M branch-misses=2.71277
BM_blackScholesScalar/25600     645090 ns       645058 ns         1094 CYCLES=2.53839M INSTRUCTIONS=6.94139M branch-misses=285.442


bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,cache-misses
--------------------------------------------------------------------------------------
Benchmark                            Time             CPU   Iterations UserCounters...
--------------------------------------------------------------------------------------
BM_blackScholesVector/1280       11255 ns        11255 ns        63023 CYCLES=52.0358k INSTRUCTIONS=123.327k cache-misses=22.1982
BM_blackScholesVector/4096       44509 ns        44501 ns        15663 CYCLES=177.204k INSTRUCTIONS=389.805k cache-misses=198.261
BM_blackScholesVector/25600     355814 ns       355739 ns         1941 CYCLES=1.2217M INSTRUCTIONS=2.423M cache-misses=2.21827k
BM_blackScholesScalar/1280       25495 ns        25492 ns        26798 CYCLES=117.034k INSTRUCTIONS=349.201k cache-misses=18.234
BM_blackScholesScalar/4096       80691 ns        80685 ns         8649 CYCLES=373.97k INSTRUCTIONS=1.11241M cache-misses=184.912
BM_blackScholesScalar/25600     643660 ns       644049 ns         1098 CYCLES=2.54184M INSTRUCTIONS=6.94139M cache-misses=1.37335k

 bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,stalled-cycles-backend
--------------------------------------------------------------------------------------
Benchmark                            Time             CPU   Iterations UserCounters...
--------------------------------------------------------------------------------------
BM_blackScholesVector/1280       11347 ns        11347 ns        62612 CYCLES=52.1702k INSTRUCTIONS=123.327k stalled-cycles-backend=4.46969
BM_blackScholesVector/4096       51382 ns        51226 ns        14939 CYCLES=193.479k INSTRUCTIONS=389.805k stalled-cycles-backend=1.89945k
BM_blackScholesVector/25600     357645 ns       357199 ns         1813 CYCLES=1.22429M INSTRUCTIONS=2.423M stalled-cycles-backend=17.8277k
BM_blackScholesScalar/1280       25441 ns        25441 ns        27492 CYCLES=117.558k INSTRUCTIONS=349.201k stalled-cycles-backend=2.77724
BM_blackScholesScalar/4096       81981 ns        81980 ns         8564 CYCLES=375.036k INSTRUCTIONS=1.11241M stalled-cycles-backend=10.5652
BM_blackScholesScalar/25600     633779 ns       633790 ns         1094 CYCLES=2.52637M INSTRUCTIONS=6.94139M stalled-cycles-backend=7.12556k

bazel-bin/src/performanceTest --benchmark_perf_counters=CYCLES,INSTRUCTIONS,L1-dcache-loads
--------------------------------------------------------------------------------------
Benchmark                            Time             CPU   Iterations UserCounters...
--------------------------------------------------------------------------------------
BM_blackScholesVector/1280       11483 ns        11481 ns        59653 CYCLES=52.3814k INSTRUCTIONS=123.327k L1-dcache-loads=58.4151k
BM_blackScholesVector/4096       44472 ns        44470 ns        15781 CYCLES=177.764k INSTRUCTIONS=389.805k L1-dcache-loads=186.752k
BM_blackScholesVector/25600     355087 ns       355074 ns         1963 CYCLES=1.22997M INSTRUCTIONS=2.423M L1-dcache-loads=1.17103M
BM_blackScholesScalar/1280       25245 ns        25242 ns        27741 CYCLES=117.156k INSTRUCTIONS=349.201k L1-dcache-loads=112.104k
BM_blackScholesScalar/4096       82491 ns        82490 ns         8649 CYCLES=376.589k INSTRUCTIONS=1.11241M L1-dcache-loads=357.187k
BM_blackScholesScalar/25600     642717 ns       642714 ns         1097 CYCLES=2.53125M INSTRUCTIONS=6.94139M L1-dcache-loads=2.24953M
```

## Analysis
### Are the numbers mostly correct? 
- The actual numbers were verified utilizing online calculators
### Did vectorization work correctly
- Utilizing AMD uPROF we see that there was vectorization occuring:

- Utilizing the compiler output, we see that:

### Performance speed up?
- There was a 2x performance improvement, which is below the theoretical of 4
- We see that there are ~1/3 the instructions present
- Branch prediction - doesn't really suffer, about the same 
- The L1 dcache loads are lower, mayybe ~1/2
- Cache misses (L3 cache misses?) are higher by like 50%
- Backend estalls are also higher -- much much higher
### Improvements to Cpp Code
#### Original Design (V1)
   - For experimental purposes I used:
   - Raw Pointers to ints
   - Double Pointers + Pass by Pointers for the setup
   - Pass by Pointer for the main calculation method
   - This was done to play with C++ code, vs write something super efficient

#### New Design (V2)
   - This will include the following:
   - Better Cache locality
   - Pass by Reference/Smart Pointer (no value passing, double pointers, or raw pointers)
   

## Extra Notes
### Passing by Value vs Refrence vs Pointer& The Impact of it
1) You can pass an object by value (method takes object, you pass object)
2) You can pass an object by reference (method takes in &var, you pass in regular var)
3) You can pass a pointer to the object by pointer (method takes in var*, you pass in the pointer, or &actual_obj_value)
   Note: similar performance to passing by reference
   Note: if you create some Object * = &actual_obj, then just passing RHS works
   Note: if you have a pointer and do &pointer, you get a double pointer (address of the pointer variable, which then points to the actual value address, which contains value). This is double pointer, In the function you call, you'll see a double pointer
- However, using double pointers is bad (adds performance overhead due to pointer chasing)
- Passing by value can be bad as well. Passing by pointer ~ passing by reference, so that is fine

### Pointer vs Reference vs Smart Pointer
   - For a simple example, we don't need to use smart pointer (useful for life cycle management)
   - Nowadays smart pointers are fully reccomended, so we should use them
   - References can be more effective, if  the object is small (saves on memory pointer chasing?)
   - Pointers and references make most sense when the object is small, but with a large object, you'll probably prefer pointers
      - Pointers make sense when dealing with large objects in memory, and having the ability to have a small variable to point to large object
      - Additionally, given we have no special values (nullPtr) or ownership transfer of dynamically allocated memory (roughly) we don't need that

### Pointer Indirection
   - AKA pointer chasing, is when you have pointer to a pointer, this causes a performance hit, as each pointer leads to a memory access, and with multiple pointers, the data might not be localized well --> might have to go to higher levels of cache or even RAM

- Branchy Code
- Cache Friendly Code 
- Alternate Allocators