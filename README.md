 Performance Testing
-
- This is a java/cpp repository designed to compare different implementations of the Black Scholes formula (and other things) with regards to performance
- Ultimately, I want to compare implementations of this formula in other languages as well (not there yet!)
- Perhaps one day other things like Monte-Carlo simulations and matrix multiplications.
- Currently, we just compare scalar and vectorized implementations of the Black Scholes formula

Setup
-
- Note: for debugging please install the hsdis library
- Note: If your processor doesn't support AVX-256 you'll see diminished gains vs what is seen here
    - See this link: https://blogs.oracle.com/javamagazine/post/java-hotspot-hsdis-disassembler
- **Pre-requisites:**
    - Install JDK17 onto your machine (17.0.4 or later)
    - https://www.oracle.c om/java/technologies/downloads/#java17 for more information
- **Running the JAR:**
    - The main jar doesn't do anything that important, it simply creates an array, runs the Black Scholes calculations
      and does a rough time difference. THIS IS *NOT* benchmarking. Just a casual run.
        - Run using: java --enable-preview --add-modules jdk.incubator.vector -jar javaPerf.jar
        - Or run using: gradle run
    - You can run the actual benchmarking as:
        - Run using: java --enable-preview --add-modules jdk.incubator.vector -jar javaPerf-jmh.jar
        - Or run using: gradle jmh
    - And you can enable diagnostic logging with the following::
        - java --enable-preview --add-modules jdk.incubator.vector -XX:+UnlockDiagnosticVMOptions -XX:+PrintIntrinsics -XX:+PrintAssembly -jar javaPerf.jar
        - Or with: gradle run -Pdebug=True
- **Building from source:**
    - Just need JDK17 installed, the gradlew script will download the correct version of gradle for use
    - gradle build - does as it says and also runs the tests

Motivations
-
- Both software and hardware have been progressing quickly. Java has moved to its fast release cycles with new features, and wider vector
  instructions are becoming more mainstream in CPUs
- As a result, I became interested in exploring this topic as we can use these tools to create more performant java code.
- More performant code allows us to create better systems utilizing our expensive hardware better, which in turns lowers
  costs, and leads to a better user experience (for example if we can calculate options faster)

What are SIMD (Single Instruction Multiple Data) Instructions?
- 
- Some basic reading:
    - https://blogs.oracle.com/javamagazine/post/java-vector-api-simd
    - https://en.wikipedia.org/wiki/Single_instruction,_multiple_data#Advantages
    - https://sites.cs.ucsb.edu/~tyang/class/240a17/slides/SIMD.pdf
- These links explain it far better than I ever could, but I'll try giving an explanation below:
- Typically, when a processor does a computation, it's executing an instruction specified by the ISA on some operands such as 
  two register values. 
    - However, these instructions will add the values stored at these two registers together, but that isn't great if you need
    to add X1...Xn to Y1...Yn
- Recently, new CPU's are being released that support new vector instructions. These instructions can operate on operands, such as
  two registers, that contain multiple data items
    - For example, you may see something like this in your compiled file
```
add    rsi,r10
```
  - But a processor capable of doing AVX-256 would do:
```
vaddpd    ymm0,ymm0,ymm1
```
- Where the registers ymm0,ymm0 hold several (2-8) double precision fp values (https://www.felixcloutier.com/x86/addpd) 
- This is typically useful in numerical computations such as polynomial evaluation, matrix math, and financial formulas,
  where you may have large arrays of data, and you need to perform identical operations to large amounts of data
- Thus, you can expect a speedup if you're processing  multiple lanes (multiple items) or more of the array at once
- You can also conditionally execute on certain lanes, and not others using a concept known as "masks" (basically
  incorporating  conditional if/else statements into this paradigm)

Methodology
-
- Built using
    - Gradle 7.5.1
    - Java 17 (17.0.4)
    - On WSL2 (5.15.57.1-microsoft-standard-WSL2) and Ubuntu 20.04.5 LTS
- All results were obtained on a Ryzen 5800X (has AVX-256 support) with PBO enabled, 3200Mhz RAM (XMP enabled).
- The benchmarks were obtained using JMH utilising the perf, stack, and gc profilers
    - Each benchmark had 5 warmup periods, followed by 5 iterations per fork, and 5 forks per test
    - Runs for 10 seconds per iteration
    - A warmup period of 5 allows appropriate JVM optimizations to be done I believe

### Black Scholes Implementation ### 
- The Black Scholes equation we wrote here is an implementation of the Black 76 formula for European Options.
    - This is an "an adjustment of his earlier and more famous Black-Scholes options pricing model" written in 1973
      (Source: https://www.investopedia.com/terms/b/blacksmodel.asp)
    - I believe the main difference is the use of spot price vs forward price and risk-free rate?
- For the scalar equation, I wrote implementations using the cumulative probability from Apache's Math library but also
  a second one using this link:
    - https://stackoverflow.com/questions/442758/which-java-library-computes-the-cumulative-standard-normal-distribution-function
- I did this because I needed a CDF function in my vector Implementation. I decided that this was the easiest first option
    - This is the thing being tested, not the apache maths version.
- Also a big thanks to: https://github.com/bret-blackford/black-scholes/blob/master/OptionValuation/src/mBret/options/Black_76.java
  for being a reference guide/sanity check.
- I may implement different Cumulative Distributive functions/Alternative Black Scholes

### Important Metrics to Consider: ####
- The most important metric is total throughput in ops/seconds (how many times the jmh framework can run the benchmark in a second)
- Afterwards, it is the memory allocation rate in mb/s
    - It's the rate of memory being used inside the Young Gen (Which is part of the JVM heap)
    - The Young Gen is split by Eden, S0, S1.
    - The GC of the Young Gen is defined as Minor GC. You have your "Old Mem" and major GC, both of which are still part of the heap mem
    - Online, it seems < 1GB/second is fine. Not sure what the upper limit would be - I believe DDR4 bandwith to CPU is 25GB/s
      and ~25GB for a Zen3 core I believe
- Memory allocation rates are important because the more memory being allocated,the more GC tends to be done
    - GC pauses threads while it collects garbage/not used objects - introducing latency and potentially stutter
    - In order to have millisecond level optimization -> ensure your application is not destroying the GC
    - We can use these to see the effects of different GC's
- IPC and other low level metrics such as cache misses
    - IPC refers to Instructions Per Cycle This is more of a fun metric. Typically, higher IPC given identical frequencies mean
      your program will finish faster, but not if the instructions are say, doing more work (like with SIMD)
    - Really more for general understanding of software and hardware together
  
### What is inlining and why do we care? ###
  - Inlining is an optimization done by the JVM. it takes methods which run 10,000x and under 325 bytes (along with other conditions), 
    and rather than call the method (method calling does have overhead!) - the JVM puts the called method inside the callee
    - Registers/program counters at the CPU level are affected
    - Source: https://jbaker.io/2022/06/09/vectors-in-java/ 
 Generally, this was a helpful source: https://ionutbalosin.com/2020/01/hotspot-jvm-performance-tuning-guidelines/
 https://www.oracle.com/java/technologies/javase/vmoptions-jsp.html 

### Goals of Testing ###
- We want to compare vector and scalar implementations of the Black Scholes formula, and figure out if there are optimizations we can do to 
  improve performance 
- Some adhoc testing not documented here showed problems with warmup periods and a problem of huge array sizes
  Thus, I decided to compare vector and scalar formulas alongside different GC and parameters
- (Going forward, the optimal parameters for the vectorized benchmark will be used)
- We tested 2 Black Scholes implementations (Scalar and Vector) over array sizes of 25600,256000 and 2560000 sized arrays
    - with different GC (G1(Default),ZGC, ParellelGC)
    - with different inlining parameters:
    - "-XX:CompileThreshold=25 or 50" (default: 10,000(?))
    - "-XX:InlineSmallCode=100, 150, 1000, or 2500"  (default: 2000)
    - "-XX:MaxInlineSize=100 or 150"    (default: 35)
    - The goal of the inlining parameters is to make the JVM inline more 
        - I realize I made some values such as InlineSmallCode but it seems that can make things better?
    - I test various combinations of the above parameters
- Thus, we compare scalar and vector and the effects of garbage collection and inlining


Results
-
### Table 1: Overview of Throughput, IPC, and Memory Allocation of the 3 implementations across all array sizes ###
```
Benchmark                                                                                                                  (arraySize)   Mode  Cnt          Score           Error      Units
blackScholes.TestJavaPerf.testScalarPerformanceDefault                                                                           25600  thrpt   25       1146.695 ±        19.868      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·cpi                                                                      25600  thrpt               0.397                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.alloc.rate                                                            25600  thrpt   25          0.518 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.alloc.rate.norm                                                       25600  thrpt   25        497.328 ±        20.785       B/op
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.count                                                                 25600  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·ipc                                                                      25600  thrpt               2.519                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·perf                                                                     25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·stack                                                                    25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceDefault                                                                          256000  thrpt   25        115.568 ±         1.033      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·cpi                                                                     256000  thrpt               0.395                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.alloc.rate                                                           256000  thrpt   25          0.517 ±         0.021     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.alloc.rate.norm                                                      256000  thrpt   25       4926.982 ±       189.812       B/op
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.count                                                                256000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·ipc                                                                     256000  thrpt               2.532                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·perf                                                                    256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·stack                                                                   256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceDefault                                                                         2560000  thrpt   25         11.576 ±         0.038      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·cpi                                                                    2560000  thrpt               0.393                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.alloc.rate                                                          2560000  thrpt   25          0.518 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.alloc.rate.norm                                                     2560000  thrpt   25      49229.901 ±      1851.027       B/op
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·gc.count                                                               2560000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·ipc                                                                    2560000  thrpt               2.542                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·perf                                                                   2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceDefault:·stack                                                                  2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceNoInline                                                                          25600  thrpt   25       1138.020 ±        17.195      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·cpi                                                                     25600  thrpt               0.397                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate                                                           25600  thrpt   25          0.517 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate.norm                                                      25600  thrpt   25        500.305 ±        20.134       B/op
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.count                                                                25600  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·ipc                                                                     25600  thrpt               2.520                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·perf                                                                    25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·stack                                                                   25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceNoInline                                                                         256000  thrpt   25        114.728 ±         0.772      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·cpi                                                                    256000  thrpt               0.395                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate                                                          256000  thrpt   25          0.516 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate.norm                                                     256000  thrpt   25       4955.567 ±       197.297       B/op
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.count                                                               256000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·ipc                                                                    256000  thrpt               2.531                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·perf                                                                   256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·stack                                                                  256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceNoInline                                                                        2560000  thrpt   25         11.508 ±         0.066      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·cpi                                                                   2560000  thrpt               0.394                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate                                                         2560000  thrpt   25          0.516 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate.norm                                                    2560000  thrpt   25      49392.797 ±      1929.334       B/op
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.count                                                              2560000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·ipc                                                                   2560000  thrpt               2.538                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·perf                                                                  2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·stack                                                                 2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault                                                             25600  thrpt   25       1145.012 ±        19.326      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·cpi                                                        25600  thrpt               0.395                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.alloc.rate                                              25600  thrpt   25          0.519 ±         0.021     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.alloc.rate.norm                                         25600  thrpt   25        499.183 ±        19.391       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.count                                                   25600  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·ipc                                                        25600  thrpt               2.529                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·perf                                                       25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·stack                                                      25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault                                                            256000  thrpt   25        115.592 ±         0.489      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·cpi                                                       256000  thrpt               0.393                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.alloc.rate                                             256000  thrpt   25          0.518 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.alloc.rate.norm                                        256000  thrpt   25       4936.691 ±       199.223       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.count                                                  256000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·ipc                                                       256000  thrpt               2.547                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·perf                                                      256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·stack                                                     256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault                                                           2560000  thrpt   25         11.549 ±         0.040      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·cpi                                                      2560000  thrpt               0.394                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.alloc.rate                                            2560000  thrpt   25          0.518 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.alloc.rate.norm                                       2560000  thrpt   25      49402.345 ±      1871.204       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·gc.count                                                 2560000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·ipc                                                      2560000  thrpt               2.535                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·perf                                                     2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCDefault:·stack                                                    2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining                                                            25600  thrpt   25       1088.063 ±         3.543      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·cpi                                                       25600  thrpt               0.391                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.alloc.rate                                             25600  thrpt   25          0.529 ±         0.021     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.alloc.rate.norm                                        25600  thrpt   25        535.742 ±        21.856       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.count                                                  25600  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·ipc                                                       25600  thrpt               2.556                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·perf                                                      25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·stack                                                     25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining                                                           256000  thrpt   25        110.530 ±         2.951      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·cpi                                                      256000  thrpt               0.390                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.alloc.rate                                            256000  thrpt   25          0.528 ±         0.021     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.alloc.rate.norm                                       256000  thrpt   25       5266.199 ±       248.668       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.count                                                 256000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·ipc                                                      256000  thrpt               2.566                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·perf                                                     256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·stack                                                    256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining                                                          2560000  thrpt   25         11.416 ±         0.336      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·cpi                                                     2560000  thrpt               0.384                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.alloc.rate                                           2560000  thrpt   25          0.527 ±         0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.alloc.rate.norm                                      2560000  thrpt   25      50894.504 ±      2528.796       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·gc.count                                                2560000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·ipc                                                     2560000  thrpt               2.604                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·perf                                                    2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGCInlining:·stack                                                   2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault                                                                    25600  thrpt   25       1166.803 ±        34.676      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·cpi                                                               25600  thrpt               0.390                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.alloc.rate                                                     25600  thrpt   25          0.750 ±         0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.alloc.rate.norm                                                25600  thrpt   25        708.869 ±        29.753       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.count                                                          25600  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·ipc                                                               25600  thrpt               2.561                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·perf                                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·stack                                                             25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault                                                                   256000  thrpt   25        118.789 ±         0.248      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·cpi                                                              256000  thrpt               0.381                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.alloc.rate                                                    256000  thrpt   25          0.749 ±         0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.alloc.rate.norm                                               256000  thrpt   25       6943.642 ±       264.151       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.count                                                         256000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·ipc                                                              256000  thrpt               2.622                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·perf                                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·stack                                                            256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault                                                                  2560000  thrpt   25         11.877 ±         0.035      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·cpi                                                             2560000  thrpt               0.383                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.alloc.rate                                                   2560000  thrpt   25          0.749 ±         0.027     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.alloc.rate.norm                                              2560000  thrpt   25      69448.957 ±      2447.025       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·gc.count                                                        2560000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·ipc                                                             2560000  thrpt               2.614                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·perf                                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDefault:·stack                                                           2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining                                                                   25600  thrpt   25       1052.682 ±         2.216      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·cpi                                                              25600  thrpt               0.397                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.alloc.rate                                                    25600  thrpt   25          0.767 ±         0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.alloc.rate.norm                                               25600  thrpt   25        802.292 ±        31.047       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.count                                                         25600  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·ipc                                                              25600  thrpt               2.517                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·perf                                                             25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·stack                                                            25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining                                                                  256000  thrpt   25        104.904 ±         0.161      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·cpi                                                             256000  thrpt               0.397                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.alloc.rate                                                   256000  thrpt   25          0.766 ±         0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.alloc.rate.norm                                              256000  thrpt   25       8041.759 ±       302.278       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.count                                                        256000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·ipc                                                             256000  thrpt               2.520                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·perf                                                            256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·stack                                                           256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining                                                                 2560000  thrpt   25         10.998 ±         0.442      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·cpi                                                            2560000  thrpt               0.388                  clks/insn
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.alloc.rate                                                  2560000  thrpt   25          0.763 ±         0.028     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.alloc.rate.norm                                             2560000  thrpt   25      76603.324 ±      4338.697       B/op
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·gc.count                                                       2560000  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·ipc                                                            2560000  thrpt               2.577                  insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·perf                                                           2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCInlining:·stack                                                          2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC                                                                     25600  thrpt   25       3404.543 ±         8.631      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·cpi                                                                25600  thrpt               0.432                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate                                                      25600  thrpt   25          0.519 ±         0.021     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate.norm                                                 25600  thrpt   25        167.768 ±         6.565       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.count                                                           25600  thrpt   25            ≈ 0                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·ipc                                                                25600  thrpt               2.316                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·perf                                                               25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·stack                                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC                                                                    256000  thrpt   25        109.328 ±         2.128      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·cpi                                                               256000  thrpt               0.651                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate                                                     256000  thrpt   25       4779.429 ±        93.031     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate.norm                                                256000  thrpt   25   48133422.685 ±       226.868       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Eden_Space                                            256000  thrpt   25       4793.792 ±       289.661     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Eden_Space.norm                                       256000  thrpt   25   48282773.556 ±   2819557.492       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Survivor_Space                                        256000  thrpt   25          0.001 ±         0.001     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Survivor_Space.norm                                   256000  thrpt   25         12.463 ±         4.010       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.count                                                          256000  thrpt   25        155.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.time                                                           256000  thrpt   25        376.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·ipc                                                               256000  thrpt               1.536                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·perf                                                              256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·stack                                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC                                                                   2560000  thrpt   25          8.078 ±         0.033      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·cpi                                                              2560000  thrpt               0.893                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate                                                    2560000  thrpt   25       3532.603 ±        14.466     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate.norm                                               2560000  thrpt   25  481352893.558 ±      2612.501       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Eden_Space                                           2560000  thrpt   25       3509.733 ±       176.829     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Eden_Space.norm                                      2560000  thrpt   25  478233253.857 ±  24006614.282       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Survivor_Space                                       2560000  thrpt   25          0.001 ±         0.001     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.churn.G1_Survivor_Space.norm                                  2560000  thrpt   25        115.986 ±        61.392       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.count                                                         2560000  thrpt   25        122.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.time                                                          2560000  thrpt   25        276.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·ipc                                                              2560000  thrpt               1.120                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·perf                                                             2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·stack                                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining                                                          25600  thrpt   25       2338.124 ±        23.380      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·cpi                                                     25600  thrpt               0.445                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate                                           25600  thrpt   25       3479.702 ±        34.789     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate.norm                                      25600  thrpt   25    1638650.225 ±        10.135       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Eden_Space                                  25600  thrpt   25       3484.708 ±       297.104     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Eden_Space.norm                             25600  thrpt   25    1641203.133 ±    140288.905       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Survivor_Space                              25600  thrpt   25          0.001 ±         0.001     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Survivor_Space.norm                         25600  thrpt   25          0.483 ±         0.140       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.count                                                25600  thrpt   25        112.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.time                                                 25600  thrpt   25        295.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·ipc                                                     25600  thrpt               2.249                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·perf                                                    25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·stack                                                   25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining                                                         256000  thrpt   25        169.372 ±         8.232      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·cpi                                                    256000  thrpt               0.592                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate                                          256000  thrpt   25       2990.683 ±       661.195     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate.norm                                     256000  thrpt   25   19664244.844 ±   5010521.445       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Eden_Space                                 256000  thrpt   25       3001.040 ±       735.895     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Eden_Space.norm                            256000  thrpt   25   19753796.168 ±   5492244.014       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Survivor_Space                             256000  thrpt   25          0.001 ±         0.001     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Survivor_Space.norm                        256000  thrpt   25          6.143 ±         3.149       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.count                                               256000  thrpt   25         97.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.time                                                256000  thrpt   25        230.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·ipc                                                    256000  thrpt               1.688                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·perf                                                   256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·stack                                                  256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining                                                        2560000  thrpt   25         10.348 ±         0.925      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·cpi                                                   2560000  thrpt               0.899                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate                                         2560000  thrpt   25       2382.540 ±       706.676     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate.norm                                    2560000  thrpt   25  270393325.739 ± 111340116.222       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Eden_Space                                2560000  thrpt   25       2366.707 ±       805.164     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Eden_Space.norm                           2560000  thrpt   25  269858105.191 ± 121784835.736       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Survivor_Space                            2560000  thrpt   25          0.004 ±         0.006     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.churn.G1_Survivor_Space.norm                       2560000  thrpt   25        368.908 ±       613.261       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.count                                              2560000  thrpt   25         81.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.time                                               2560000  thrpt   25        186.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·ipc                                                   2560000  thrpt               1.112                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·perf                                                  2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·stack                                                 2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault                                                             25600  thrpt   25       1945.716 ±       553.073      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·cpi                                                        25600  thrpt               0.481                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.alloc.rate                                              25600  thrpt   25       4418.024 ±      1691.794     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.alloc.rate.norm                                         25600  thrpt   25    3072335.757 ±   1179064.242       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Eden_Space                                     25600  thrpt   25       4460.432 ±      1723.905     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Eden_Space.norm                                25600  thrpt   25    3102161.282 ±   1202155.217       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Survivor_Space                                 25600  thrpt   25          0.008 ±         0.005     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Survivor_Space.norm                            25600  thrpt   25          5.787 ±         3.261       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.count                                                   25600  thrpt   25        143.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.time                                                    25600  thrpt   25         92.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·ipc                                                        25600  thrpt               2.080                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·perf                                                       25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·stack                                                      25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault                                                            256000  thrpt   25        135.832 ±         0.667      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·cpi                                                       256000  thrpt               0.567                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.alloc.rate                                             256000  thrpt   25       4927.285 ±        24.195     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.alloc.rate.norm                                        256000  thrpt   25   39940368.976 ±       159.585       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Eden_Space                                    256000  thrpt   25       4947.155 ±       287.578     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Eden_Space.norm                               256000  thrpt   25   40099923.913 ±   2306145.067       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Survivor_Space                                256000  thrpt   25          0.012 ±         0.004     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Survivor_Space.norm                           256000  thrpt   25         99.328 ±        29.598       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.count                                                  256000  thrpt   25        159.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.time                                                   256000  thrpt   25        113.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·ipc                                                       256000  thrpt               1.762                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·perf                                                      256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·stack                                                     256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault                                                           2560000  thrpt   25         11.880 ±         0.134      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·cpi                                                      2560000  thrpt               0.659                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.alloc.rate                                            2560000  thrpt   25       4310.457 ±        48.313     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.alloc.rate.norm                                       2560000  thrpt   25  399409930.055 ±      1874.503       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Eden_Space                                   2560000  thrpt   25       4375.277 ±       218.037     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Eden_Space.norm                              2560000  thrpt   25  405485970.889 ±  20523253.571       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Survivor_Space                               2560000  thrpt   25          0.007 ±         0.003     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.churn.PS_Survivor_Space.norm                          2560000  thrpt   25        624.533 ±       285.489       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.count                                                 2560000  thrpt   25        145.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·gc.time                                                  2560000  thrpt   25        137.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·ipc                                                      2560000  thrpt               1.518                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·perf                                                     2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCDefault:·stack                                                    2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining                                                            25600  thrpt   25       2209.888 ±       136.709      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·cpi                                                       25600  thrpt               0.422                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.alloc.rate                                             25600  thrpt   25       5132.106 ±       948.737     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.alloc.rate.norm                                        25600  thrpt   25    2621710.852 ±    613664.788       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Eden_Space                                    25600  thrpt   25       5177.340 ±       968.024     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Eden_Space.norm                               25600  thrpt   25    2644120.511 ±    621210.741       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Survivor_Space                                25600  thrpt   25          0.011 ±         0.004     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Survivor_Space.norm                           25600  thrpt   25          5.405 ±         2.134       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.count                                                  25600  thrpt   25        166.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.time                                                   25600  thrpt   25        111.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·ipc                                                       25600  thrpt               2.370                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·perf                                                      25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·stack                                                     25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining                                                           256000  thrpt   25        197.537 ±         4.967      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·cpi                                                      256000  thrpt               0.524                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.alloc.rate                                            256000  thrpt   25       2939.951 ±        73.920     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.alloc.rate.norm                                       256000  thrpt   25   16386936.174 ±       128.933       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Eden_Space                                   256000  thrpt   25       2967.596 ±       217.029     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Eden_Space.norm                              256000  thrpt   25   16538252.124 ±   1128966.011       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Survivor_Space                               256000  thrpt   25          0.011 ±         0.005     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Survivor_Space.norm                          256000  thrpt   25         59.834 ±        26.389       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.count                                                 256000  thrpt   25         96.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.time                                                  256000  thrpt   25         86.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·ipc                                                      256000  thrpt               1.907                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·perf                                                     256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·stack                                                    256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining                                                          2560000  thrpt   25         16.351 ±         0.072      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·cpi                                                     2560000  thrpt               0.638                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.alloc.rate                                           2560000  thrpt   25       2433.939 ±        10.747     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.alloc.rate.norm                                      2560000  thrpt   25  163875539.114 ±      1338.760       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Eden_Space                                  2560000  thrpt   25       2382.535 ±       268.509     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Eden_Space.norm                             2560000  thrpt   25  160421020.627 ±  18102223.073       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Survivor_Space                              2560000  thrpt   25          0.004 ±         0.003     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.churn.PS_Survivor_Space.norm                         2560000  thrpt   25        271.402 ±       177.221       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.count                                                2560000  thrpt   25         85.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·gc.time                                                 2560000  thrpt   25         80.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·ipc                                                     2560000  thrpt               1.567                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·perf                                                    2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGCInlining:·stack                                                   2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault                                                                    25600  thrpt   25       2441.893 ±       542.131      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·cpi                                                               25600  thrpt               0.459                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.alloc.rate                                                     25600  thrpt   25       2547.146 ±      2384.507     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.alloc.rate.norm                                                25600  thrpt   25    1782203.310 ±   1668509.267       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.ZHeap                                                    25600  thrpt   25       2527.782 ±      2367.343     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.ZHeap.norm                                               25600  thrpt   25    1768874.598 ±   1656585.305       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.count                                                          25600  thrpt   25        996.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.time                                                           25600  thrpt   25       1662.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·ipc                                                               25600  thrpt               2.180                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·perf                                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·stack                                                             25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault                                                                   256000  thrpt   25        152.664 ±         1.122      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·cpi                                                              256000  thrpt               0.467                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.alloc.rate                                                    256000  thrpt   25       6177.023 ±        45.393     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.alloc.rate.norm                                               256000  thrpt   25   44550504.341 ±       224.232       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.CodeHeap_'non-nmethods'                                 256000  thrpt   25          0.002 ±         0.008     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.CodeHeap_'non-nmethods'.norm                            256000  thrpt   25         14.955 ±        56.013       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.ZHeap                                                   256000  thrpt   25       6131.098 ±        51.034     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.ZHeap.norm                                              256000  thrpt   25   44219490.149 ±    201869.893       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.count                                                         256000  thrpt   25       1468.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.time                                                          256000  thrpt   25       4272.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·ipc                                                              256000  thrpt               2.144                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·perf                                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·stack                                                            256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault                                                                  2560000  thrpt   25         13.211 ±         1.917      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·cpi                                                             2560000  thrpt               0.457                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.alloc.rate                                                   2560000  thrpt   25       5343.996 ±       775.534     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.alloc.rate.norm                                              2560000  thrpt   25  445520146.803 ±     52974.175       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.ZHeap                                                  2560000  thrpt   25       5174.130 ±       745.127     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.churn.ZHeap.norm                                             2560000  thrpt   25  437251568.794 ±  32524409.999       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.count                                                        2560000  thrpt   25        499.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·gc.time                                                         2560000  thrpt   25      23818.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·ipc                                                             2560000  thrpt               2.189                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·perf                                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDefault:·stack                                                           2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger                                               25600  thrpt   25       1854.460 ±       442.834      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·cpi                                          25600  thrpt               0.470                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.alloc.rate                                25600  thrpt   25       5065.569 ±      1936.299     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.alloc.rate.norm                           25600  thrpt   25    3564128.695 ±   1362329.685       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.ZHeap                               25600  thrpt   25       5011.910 ±      1916.836     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.ZHeap.norm                          25600  thrpt   25    3526439.788 ±   1348595.663       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.count                                     25600  thrpt   25       1912.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.time                                      25600  thrpt   25       3288.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·ipc                                          25600  thrpt               2.129                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·perf                                         25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·stack                                        25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger                                              256000  thrpt   25        151.785 ±         0.905      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·cpi                                         256000  thrpt               0.474                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.alloc.rate                               256000  thrpt   25       6141.502 ±        36.586     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.alloc.rate.norm                          256000  thrpt   25   44550549.548 ±       233.403       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.CodeHeap_'non-nmethods'            256000  thrpt   25          0.008 ±         0.022     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.CodeHeap_'non-nmethods'.norm       256000  thrpt   25         60.501 ±       156.858       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.ZHeap                              256000  thrpt   25       6107.182 ±        73.790     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.ZHeap.norm                         256000  thrpt   25   44302099.946 ±    493947.398       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.count                                    256000  thrpt   25       1480.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.time                                     256000  thrpt   25       4263.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·ipc                                         256000  thrpt               2.108                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·perf                                        256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·stack                                       256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger                                             2560000  thrpt   25         13.851 ±         0.290      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·cpi                                        2560000  thrpt               0.456                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.alloc.rate                              2560000  thrpt   25       5602.442 ±       117.335     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.alloc.rate.norm                         2560000  thrpt   25  445504935.302 ±      2412.905       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.ZHeap                             2560000  thrpt   25       5331.425 ±       223.018     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.churn.ZHeap.norm                        2560000  thrpt   25  423904611.480 ±  14637289.258       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.count                                   2560000  thrpt   25        497.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·gc.time                                    2560000  thrpt   25      23842.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·ipc                                        2560000  thrpt               2.192                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·perf                                       2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger:·stack                                      2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150                                                    25600  thrpt   25       2328.490 ±        16.775      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·cpi                                               25600  thrpt               0.405                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.alloc.rate                                     25600  thrpt   25       3898.891 ±        28.071     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.alloc.rate.norm                                25600  thrpt   25    1843673.698 ±        13.290       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.ZHeap                                    25600  thrpt   25       3869.141 ±        62.151     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.ZHeap.norm                               25600  thrpt   25    1829531.427 ±     22992.227       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.count                                          25600  thrpt   25       2392.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.time                                           25600  thrpt   25       4046.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·ipc                                               25600  thrpt               2.472                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·perf                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·stack                                             25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150                                                   256000  thrpt   25        214.686 ±         2.422      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·cpi                                              256000  thrpt               0.424                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.alloc.rate                                    256000  thrpt   25       3594.807 ±        40.583     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.alloc.rate.norm                               256000  thrpt   25   18436663.829 ±       107.606       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.ZHeap                                   256000  thrpt   25       3568.930 ±        61.841     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.ZHeap.norm                              256000  thrpt   25   18303994.642 ±    242938.685       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.count                                         256000  thrpt   25       1496.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.time                                          256000  thrpt   25       4244.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·ipc                                              256000  thrpt               2.356                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·perf                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·stack                                            256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150                                                  2560000  thrpt   25         20.750 ±         0.541      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·cpi                                             2560000  thrpt               0.418                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.alloc.rate                                   2560000  thrpt   25       3474.695 ±        90.573     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.alloc.rate.norm                              2560000  thrpt   25  184362077.758 ±      1664.058       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.CodeHeap_'non-nmethods'                2560000  thrpt   25          0.002 ±         0.008     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.CodeHeap_'non-nmethods'.norm           2560000  thrpt   25        119.564 ±       447.815       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.ZHeap                                  2560000  thrpt   25       3472.692 ±       474.089     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.churn.ZHeap.norm                             2560000  thrpt   25  184059221.337 ±  24182965.529       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.count                                        2560000  thrpt   25        325.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·gc.time                                         2560000  thrpt   25       5874.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·ipc                                             2560000  thrpt               2.390                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·perf                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_25_200_150:·stack                                           2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100                                                    25600  thrpt   25       2242.852 ±       112.783      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·cpi                                               25600  thrpt               0.406                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.alloc.rate                                     25600  thrpt   25       4411.756 ±       824.089     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.alloc.rate.norm                                25600  thrpt   25    2212334.175 ±    563698.678       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.ZHeap                                    25600  thrpt   25       4392.009 ±       819.382     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.ZHeap.norm                               25600  thrpt   25    2202429.157 ±    560612.288       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.count                                          25600  thrpt   25       2380.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.time                                           25600  thrpt   25       4031.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·ipc                                               25600  thrpt               2.464                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·perf                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·stack                                             25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100                                                   256000  thrpt   25        210.678 ±         7.172      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·cpi                                              256000  thrpt               0.421                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.alloc.rate                                    256000  thrpt   25       4176.411 ±       885.108     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.alloc.rate.norm                               256000  thrpt   25   22123182.429 ±   5636855.414       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.ZHeap                                   256000  thrpt   25       4151.021 ±       882.500     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.ZHeap.norm                              256000  thrpt   25   21988902.809 ±   5617254.596       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.count                                         256000  thrpt   25       1516.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.time                                          256000  thrpt   25       4281.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·ipc                                              256000  thrpt               2.378                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·perf                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·stack                                            256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100                                                  2560000  thrpt   25         20.851 ±         0.363      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·cpi                                             2560000  thrpt               0.417                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.alloc.rate                                   2560000  thrpt   25       3491.647 ±        60.866     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.alloc.rate.norm                              2560000  thrpt   25  184361768.607 ±      1702.637       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.CodeHeap_'non-nmethods'                2560000  thrpt   25          0.006 ±         0.024     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.CodeHeap_'non-nmethods'.norm           2560000  thrpt   25        323.493 ±      1211.611       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.ZHeap                                  2560000  thrpt   25       3483.277 ±       293.058     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.churn.ZHeap.norm                             2560000  thrpt   25  183986905.124 ±  15576530.007       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.count                                        2560000  thrpt   25        328.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·gc.time                                         2560000  thrpt   25       6036.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·ipc                                             2560000  thrpt               2.396                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·perf                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_all_50_150_100:·stack                                           2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200                                                25600  thrpt   25       2339.324 ±        30.756      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·cpi                                           25600  thrpt               0.400                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.alloc.rate                                 25600  thrpt   25       3917.063 ±        51.500     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.alloc.rate.norm                            25600  thrpt   25    1843672.743 ±        14.227       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.ZHeap                                25600  thrpt   25       3883.709 ±        68.997     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.ZHeap.norm                           25600  thrpt   25    1828086.888 ±     26826.461       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.count                                      25600  thrpt   25       2404.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.time                                       25600  thrpt   25       4019.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·ipc                                           25600  thrpt               2.502                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·perf                                          25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·stack                                         25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200                                               256000  thrpt   25        212.420 ±         3.138      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·cpi                                          256000  thrpt               0.431                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.alloc.rate                                256000  thrpt   25       3556.869 ±        52.544     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.alloc.rate.norm                           256000  thrpt   25   18436710.220 ±       128.482       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.CodeHeap_'non-nmethods'             256000  thrpt   25          0.006 ±         0.024     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.CodeHeap_'non-nmethods'.norm        256000  thrpt   25         33.492 ±       125.440       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.ZHeap                               256000  thrpt   25       3543.605 ±        83.886     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.ZHeap.norm                          256000  thrpt   25   18366392.016 ±    287493.750       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.count                                     256000  thrpt   25       1488.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.time                                      256000  thrpt   25       4255.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·ipc                                          256000  thrpt               2.321                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·perf                                         256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·stack                                        256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200                                              2560000  thrpt   25         20.499 ±         0.576      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·cpi                                         2560000  thrpt               0.420                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.alloc.rate                               2560000  thrpt   25       3432.439 ±        96.675     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.alloc.rate.norm                          2560000  thrpt   25  184362558.212 ±      1942.095       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.CodeHeap_'non-nmethods'            2560000  thrpt   25          0.002 ±         0.008     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.CodeHeap_'non-nmethods'.norm       2560000  thrpt   25        107.329 ±       401.992       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.ZHeap                              2560000  thrpt   25       3420.472 ±       358.958     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.churn.ZHeap.norm                         2560000  thrpt   25  183981059.915 ±  19974800.770       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.count                                    2560000  thrpt   25        319.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·gc.time                                     2560000  thrpt   25       6953.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·ipc                                         2560000  thrpt               2.382                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·perf                                        2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compile25Inline200:·stack                                       2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller                                                    25600  thrpt   25       2144.375 ±       551.450      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·cpi                                               25600  thrpt               0.464                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.alloc.rate                                     25600  thrpt   25       3776.688 ±      2357.244     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.alloc.rate.norm                                25600  thrpt   25    2673169.270 ±   1668509.846       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.ZHeap                                    25600  thrpt   25       3751.683 ±      2342.543     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.ZHeap.norm                               25600  thrpt   25    2655568.000 ±   1657994.039       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.count                                          25600  thrpt   25       1476.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.time                                           25600  thrpt   25       2450.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·ipc                                               25600  thrpt               2.157                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·perf                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·stack                                             25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller                                                   256000  thrpt   25        150.881 ±         1.737      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·cpi                                              256000  thrpt               0.473                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.alloc.rate                                    256000  thrpt   25       6104.933 ±        70.287     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.alloc.rate.norm                               256000  thrpt   25   44550562.046 ±       231.361       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.CodeHeap_'non-nmethods'                 256000  thrpt   25          0.002 ±         0.008     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.CodeHeap_'non-nmethods'.norm            256000  thrpt   25         15.613 ±        58.476       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.ZHeap                                   256000  thrpt   25       6055.697 ±        89.854     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.ZHeap.norm                              256000  thrpt   25   44190876.887 ±    393543.489       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.count                                         256000  thrpt   25       1436.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.time                                          256000  thrpt   25       4200.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·ipc                                              256000  thrpt               2.116                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·perf                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·stack                                            256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller                                                  2560000  thrpt   25         13.515 ±         0.481      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·cpi                                             2560000  thrpt               0.461                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.alloc.rate                                   2560000  thrpt   25       5466.625 ±       195.039     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.alloc.rate.norm                              2560000  thrpt   25  445506263.337 ±      2942.564       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.ZHeap                                  2560000  thrpt   25       5167.498 ±       316.619     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.churn.ZHeap.norm                             2560000  thrpt   25  421423458.009 ±  24772815.831       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.count                                        2560000  thrpt   25        497.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·gc.time                                         2560000  thrpt   25      23914.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·ipc                                             2560000  thrpt               2.171                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·perf                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_compileSmaller:·stack                                           2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger                                                      25600  thrpt   25       2425.204 ±       545.310      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·cpi                                                 25600  thrpt               0.461                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.alloc.rate                                       25600  thrpt   25       2511.805 ±      2351.268     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.alloc.rate.norm                                  25600  thrpt   25    1782204.645 ±   1668508.688       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.churn.ZHeap                                      25600  thrpt   25       2487.414 ±      2329.465     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.churn.ZHeap.norm                                 25600  thrpt   25    1765001.893 ±   1652814.988       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.count                                            25600  thrpt   25        964.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.time                                             25600  thrpt   25       1638.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·ipc                                                 25600  thrpt               2.167                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·perf                                                25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·stack                                               25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger                                                     256000  thrpt   25        148.502 ±         2.141      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·cpi                                                256000  thrpt               0.478                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.alloc.rate                                      256000  thrpt   25       6008.678 ±        86.559     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.alloc.rate.norm                                 256000  thrpt   25   44550680.135 ±       259.965       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.churn.ZHeap                                     256000  thrpt   25       5974.530 ±       121.816     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.churn.ZHeap.norm                                256000  thrpt   25   44294923.788 ±    524486.671       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.count                                           256000  thrpt   25       1452.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.time                                            256000  thrpt   25       4270.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·ipc                                                256000  thrpt               2.090                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·perf                                               256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·stack                                              256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger                                                    2560000  thrpt   25         13.478 ±         0.520      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·cpi                                               2560000  thrpt               0.465                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.alloc.rate                                     2560000  thrpt   25       5451.466 ±       210.912     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.alloc.rate.norm                                2560000  thrpt   25  445506736.662 ±      3548.606       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.churn.ZHeap                                    2560000  thrpt   25       5219.614 ±       275.872     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.churn.ZHeap.norm                               2560000  thrpt   25  426888737.938 ±  19738875.174       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.count                                          2560000  thrpt   25        509.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·gc.time                                           2560000  thrpt   25      24075.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·ipc                                               2560000  thrpt               2.152                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·perf                                              2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineBigger:·stack                                             2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller                                                     25600  thrpt   25       2358.826 ±        19.500      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·cpi                                                25600  thrpt               0.399                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.alloc.rate                                      25600  thrpt   25       3949.698 ±        32.629     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.alloc.rate.norm                                 25600  thrpt   25    1843669.807 ±        15.135       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.ZHeap                                     25600  thrpt   25       3917.309 ±        50.807     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.ZHeap.norm                                25600  thrpt   25    1828557.760 ±     18717.597       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.count                                           25600  thrpt   25       2416.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.time                                            25600  thrpt   25       4025.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·ipc                                                25600  thrpt               2.507                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·perf                                               25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·stack                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller                                                    256000  thrpt   25        213.416 ±         3.076      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·cpi                                               256000  thrpt               0.426                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.alloc.rate                                     256000  thrpt   25       3573.570 ±        51.505     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.alloc.rate.norm                                256000  thrpt   25   18436721.155 ±       131.064       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.ZHeap                                    256000  thrpt   25       3545.901 ±        72.824     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.ZHeap.norm                               256000  thrpt   25   18293861.831 ±    264065.895       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.count                                          256000  thrpt   25       1544.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.time                                           256000  thrpt   25       4326.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·ipc                                               256000  thrpt               2.350                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·perf                                              256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·stack                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller                                                   2560000  thrpt   25         20.351 ±         0.970      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·cpi                                              2560000  thrpt               0.408                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.alloc.rate                                    2560000  thrpt   25       4028.829 ±       841.169     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.alloc.rate.norm                               2560000  thrpt   25  221227662.995 ±  56369169.337       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.CodeHeap_'non-nmethods'                 2560000  thrpt   25          0.004 ±         0.016     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.CodeHeap_'non-nmethods'.norm            2560000  thrpt   25        220.821 ±       827.064       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.ZHeap                                   2560000  thrpt   25       3998.665 ±       823.704     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.churn.ZHeap.norm                              2560000  thrpt   25  219439736.766 ±  54529220.075       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.count                                         2560000  thrpt   25        383.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·gc.time                                          2560000  thrpt   25      10267.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·ipc                                              2560000  thrpt               2.451                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·perf                                             2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller:·stack                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2                                                    25600  thrpt   25       2280.064 ±        31.988      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·cpi                                               25600  thrpt               0.405                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.alloc.rate                                     25600  thrpt   25       3817.806 ±        53.561     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.alloc.rate.norm                                25600  thrpt   25    1843667.122 ±        17.096       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.CodeHeap_'non-nmethods'                  25600  thrpt   25          0.002 ±         0.008     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.CodeHeap_'non-nmethods'.norm             25600  thrpt   25          0.993 ±         3.719       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.ZHeap                                    25600  thrpt   25       3792.311 ±        63.460     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.ZHeap.norm                               25600  thrpt   25    1831472.354 ±     22983.523       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.count                                          25600  thrpt   25       2228.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.time                                           25600  thrpt   25       3679.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·ipc                                               25600  thrpt               2.472                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·perf                                              25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·stack                                             25600  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2                                                   256000  thrpt   25        202.539 ±        19.272      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·cpi                                              256000  thrpt               0.429                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.alloc.rate                                    256000  thrpt   25       4114.614 ±       786.219     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.alloc.rate.norm                               256000  thrpt   25   23659393.491 ±   7985938.457       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.ZHeap                                   256000  thrpt   25       4090.218 ±       781.298     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.ZHeap.norm                              256000  thrpt   25   23515519.314 ±   7926574.062       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.count                                         256000  thrpt   25       1480.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.time                                          256000  thrpt   25       4229.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·ipc                                              256000  thrpt               2.329                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·perf                                             256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·stack                                            256000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2                                                  2560000  thrpt   25         20.689 ±         0.562      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·cpi                                             2560000  thrpt               0.419                  clks/insn
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.alloc.rate                                   2560000  thrpt   25       3464.551 ±        94.216     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.alloc.rate.norm                              2560000  thrpt   25  184361886.687 ±      1819.778       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.CodeHeap_'non-nmethods'                2560000  thrpt   25          0.004 ±         0.011     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.CodeHeap_'non-nmethods'.norm           2560000  thrpt   25        218.310 ±       566.138       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.ZHeap                                  2560000  thrpt   25       3492.563 ±       271.340     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.churn.ZHeap.norm                             2560000  thrpt   25  186050624.053 ±  14926233.507       B/op
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.count                                        2560000  thrpt   25        328.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·gc.time                                         2560000  thrpt   25       5698.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·ipc                                             2560000  thrpt               2.387                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·perf                                            2560000  thrpt                 NaN                        ---
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCInlining_inlineSmaller2:·stack                                           2560000  thrpt                 NaN                        ---
```
### Table 2: Overview of the CPU performance using the Perf profiler (Scalar) - 2560000 sized array (1 iteration example taken) ###
```
          55321.43 msec task-clock:u              #    0.518 CPUs utilized          
                 0      context-switches:u        #    0.000 K/sec                  
                 0      cpu-migrations:u          #    0.000 K/sec                  
             16185      page-faults:u             #    0.293 K/sec                  
      236950207166      cycles:u                  #    4.283 GHz                      (39.96%)
         170822767      stalled-cycles-frontend:u #    0.07% frontend cycles idle     (40.34%)
         155268385      stalled-cycles-backend:u  #    0.07% backend cycles idle      (40.70%)
      619129828670      instructions:u            #    2.61  insn per cycle         
                                                  #    0.00  stalled cycles per insn  (41.08%)
       43667886424      branches:u                #  789.349 M/sec                    (41.35%)
          16237550      branch-misses:u           #    0.04% of all branches          (41.47%)
      158436185419      L1-dcache-loads:u         # 2863.921 M/sec                    (40.91%)
        1624122028      L1-dcache-load-misses:u   #    1.03% of all L1-dcache accesses  (41.00%)
   <not supported>      LLC-loads:u                                                 
   <not supported>      LLC-load-misses:u                                           
         412009001      L1-icache-loads:u         #    7.448 M/sec                    (40.65%)
           2648658      L1-icache-load-misses:u   #    0.64% of all L1-icache accesses  (40.50%)
          21968558      dTLB-loads:u              #    0.397 M/sec                    (40.37%)
          13114058      dTLB-load-misses:u        #   59.69% of all dTLB cache accesses  (40.13%)
           2611731      iTLB-loads:u              #    0.047 M/sec                    (39.96%)
           2760130      iTLB-load-misses:u        #  105.68% of all iTLB cache accesses  (39.56%)
        1634461137      L1-dcache-prefetches:u    #   29.545 M/sec                    (39.41%)
   <not supported>      L1-dcache-prefetch-misses:u   
```
### Table 3: Overview of the CPU performance using the Perf profiler (Vector, ZGC, optimal inlining) - 2560000 sized array (1 iteration example taken)
```
          59180.38 msec task-clock:u              #    0.555 CPUs utilized          
                 0      context-switches:u        #    0.000 K/sec                  
                 0      cpu-migrations:u          #    0.000 K/sec                  
            264564      page-faults:u             #    0.004 M/sec                  
      247625629070      cycles:u                  #    4.184 GHz                      (39.74%)
         578612493      stalled-cycles-frontend:u #    0.23% frontend cycles idle     (40.13%)
         533700732      stalled-cycles-backend:u  #    0.22% backend cycles idle      (40.82%)
      571371045812      instructions:u            #    2.31  insn per cycle         
                                                  #    0.00  stalled cycles per insn  (41.61%)
       65576158874      branches:u                # 1108.073 M/sec                    (41.85%)
          33238383      branch-misses:u           #    0.05% of all branches          (41.97%)
      251615304958      L1-dcache-loads:u         # 4251.668 M/sec                    (41.42%)
        8346193224      L1-dcache-load-misses:u   #    3.32% of all L1-dcache accesses  (41.45%)
   <not supported>      LLC-loads:u                                                 
   <not supported>      LLC-load-misses:u                                           
        1522829821      L1-icache-loads:u         #   25.732 M/sec                    (41.03%)
           6690682      L1-icache-load-misses:u   #    0.44% of all L1-icache accesses  (40.75%)
         116513404      dTLB-loads:u              #    1.969 M/sec                    (40.34%)
          83604713      dTLB-load-misses:u        #   71.76% of all dTLB cache accesses  (40.01%)
           4655708      iTLB-loads:u              #    0.079 M/sec                    (39.67%)
           4266750      iTLB-load-misses:u        #   91.65% of all iTLB cache accesses  (39.38%)
        7890574290      L1-dcache-prefetches:u    #  133.331 M/sec                    (39.25%)
   <not supported>      L1-dcache-prefetch-misses:u                                   
                             

```

Analysis
-
### Sanity Check That we're using vectorization
- It's important to *check* I'm actually using SIMD instructions - and that the benchmark differences aren't just because
  I programmed something incorrectly
- Existence of SIMD instructions may not be enough, due to auto-vectorization!
- I will try map specific lines where computation occurs to actual vectorized instructions
- 
- 
  0x00007f4240d2ab16:   vmovdqu ymm0,YMMWORD PTR [r12+r11*8+0x10]
  0x00007f4240d2ab1d:   vmulpd ymm0,ymm0,YMMWORD PTR [r12+r8*8+0x10]
  ;*invokestatic store {reexecute=0 rethrow=0 return_oop=0}
  ; - jdk.incubator.vector.DoubleVector::intoArray@42 (line 2875)
  ; - blackScholes.JavaSIMD::calculateD3@78 (line 82)
  ; - blackScholes.JavaSIMD::calculateBlackScholesSingleCycle@42 (line 50)
  0x00007f4240d2ab24:   vxorpd ymm0,ymm0,YMMWORD PTR [rip+0xfffffffff8448794]        # Stub::vector_double_sign_flip
  ;*aload_1 {reexecute=0 rethrow=0 return_oop=0}
  ; - jdk.incubator.vector.DoubleVector::lanewiseTemplate@37 (line 540)
  ; - jdk.incubator.vector.Double256Vector::lanewise@2 (line 273)
  ; - jdk.incubator.vector.Double256Vector::lanewise@2 (line 41)
  ; - jdk.incubator.vector.DoubleVector::sqrt@4 (line 1519)
  ; - blackScholes.JavaSIMD::calculateBlackScholesSingleCycle@23 (line 48)
  ;   {external_word}

- vmovdqu is a SIMD instruction which Moves 128, 256 or 512 bits of packed byte/word/doubleword/quadword integer values (https://www.felixcloutier.com/x86/movdqu:vmovdqu8:vmovdqu16:vmovdqu32:vmovdqu64)
- vmulpd is A SIMD multiplication instruction, 
- So I guess we can see the program move the data from YMMWORD PTR source to the register ymm0 which it uses to multiply and store back in YMMWORD PTR?

### Throughput in General
- Despite all the different settings, scalar performance remains roughly the same
- We see that the vector implementation is faster than both scalar implementations across every array size, except for some implementations at very
  large array sizes
**In general, I think we can see 2 to 2.22x speedup for vector Black Scholes computing arrays between 25,600 and 2.5 million, using inlining parameters and ZGC**
**We can achieve up to a 3.5x perf increase if we have an array of 25,600 elements**
**There is an impact of GC and getting inlining to work better(?) helps alot**

- Note: not shown, but we tested the scalar approach with the apache maths CDF function, and it was much slower than regular scalar [not shown here]

### Throughput with Increasing Array Sizes
- We see a non-linear decrease in ops/second as we increased array sizes
  - Likely due to GC problems. At high array sizes, the default GC (G1) does not work well
  - Changing GC's to either parallel or ZGC helped
- Modifying inline parameters
    - A mixed bag, but it definitely helped certain GC's the largest array sizes
- The scalar implementation throughput appears to be consistent across all array sizes,
- However, across different GC's, inlining parameters, vector throughput appears to decrease with increasing array size,
  more so than expected
  - G1 GC (no inline changes): <25% the expected theoretical performance at the highest array size
  - ZGC (no inline changes): ~54% of the expected theoretical performance at the highest array size
- There is an effect of the inlining parameters (explained later) that causes the overall performance drop to be MUCH smaller
  - However, the initial throughput at the smallest array sizes was also slightly lower.
  - With various inlining parameters, we got a performance drop of ~10% from the expected theoretical using the ZGC GC
- Not sure, but the array size effect could be due to the increasing GC count and time that happens at higher array sizes

### Throughput with different GC
- Currently, by default, the G1 GC is used.
- At the smallest array sizes without inlining, vector with G1 is 3.5x faster than the scalar implementation, with
    - 39% than ZGC and 54% faster than parallel
- At the largest array sizes, without inlining ZGC is 70% faster than G1, with Parallel faster than G1 by over 50% as well
    - G1 is actually slower than scalar
- Inlining does impact each GC differently. 
  - Does not help GC's at smallest array sizes
    - For G1: At lower array sizes, we saw a perf decrease of ~30%
  - Helps G1 and ParallelGC, and ZGC a bit at the largest array sizes (between 25% to 70%)
  - With equivalent inlining, at small array sizes, performance of the vectorized implementations are similar range, being ~2x the scalar one
  - At the largest array size ZGC is 2x the speed of the G1, the latter being ~ to scalar performance
- G1 fastest at smallest size, ZGC with inlining most consistent, ParallelGC in the middle
- What behavioral differences between the GC's could explain this difference?

### Throughput with different inline parameters
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=25"})
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:InlineSmallCode=200"})   **good speed** (over 20 ops/s for ZGC at largest array sizes)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:InlineSmallCode=1000"})  **good speed** (over 20 ops/s for ZGC at largest array sizes)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:InlineSmallCode=2500"})
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:MaxInlineSize=150"})
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=25","-XX:InlineSmallCode=200"}) **good speed** (over 20 ops/s for ZGC at largest array sizes)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=50","-XX:InlineSmallCode=150","-XX:MaxInlineSize=100"})  **good speed** (over 20 ops/s for ZGC at largest array sizes)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=25","-XX:InlineSmallCode=200","-XX:MaxInlineSize=150"})  **good speed** (over 20 ops/s for ZGC at largest array sizes)

- There was not a large significant effect on scalar code. I likely need to refactor scalar code to be smaller/more agile
- We see making the InLineSmalLCode parameter smaller actually increases ops/second. I would have thought the opposite.
- Other parameters such as making CompileThreshold smaller, and making MaxInlineSize bigger (to increase inlining) help as expected!
  - Currently, unsure why this is the case. An exhaustive grid search is needed 
- Overall there is a mixed bag as described above, with these parameters having no/negative impact at smallest array size, but a positive impact at larger ones
- Why? And What about the unexpected behaviour? 

### Memory Allocation/Pressure
- Looking at memory allocation characteristics, Vector Black Scholes has much higher allocation rate than either scalar implementation
- Scalar implementation stays under  1MB/seconds, while vector implementations range from 2 to 6GB/second 
  - Higher than recommended online value, while the others are well below 1MB/s
- This implies that lots of objects are being created on the heap, specifically the Eden space. 
- When doing vectorization, we're creating quite alot of DoubleVectors, resulting in large stack/big eden memories I assume
- Increasing Eden Memory decreased GC count and time significantly, (less than half the time allocated to GC) [not shown]
- With this increased eden memory, we see that scalar implementations have few GC counts - at 0 
- However, with vectorization we see wildly different things:
  - DefaultGC -> from 76counts and 200ms to maximum of 155 and 367ms
  - ParallelGC -> ~100 (100-132) with 92-95ms
  - ZGC -> ~400+ to 2452 (happened at lower array sizes?) with up to 20seconds of GC
  - Making inlining less strict seemed to help decrease this

### IPC and other metrics
- IPC of scalar implementations is around 2.5
- However, we have ~2.1-2.5 IPC with vector implementations
- Instruction count? (TBD)
- Clock speed? (TBD)
- Cache misses? (TBD)

### General Concerns
  - These results were run on a Ryzen 5800X. As a result, I cannot use AVX512, which may limit use of certain instructions
  - At one point there was a 3.5x increase which is nice, and a 2x increase at large array sizes.
  - Were my array sizes too large? 
  - Did I benchmark correctly?
  - Did I write good scalar/vector functions? 
  - GC slowing down vectorization? What is up with large array sizes
  - Method inlining? Why does decreasing one param make things faster? 

Future Work
- 
### **To Do Fixes**
- I need to update the equation to take into account dividend yield
- Add the overflow (tail elements which do not fit) case
- refactor scalar code to be smaller/more agile
- Finish IPC/cache "analysis"
- update Memory Allocation/Pressure
### **To Do New Features**
- Implement different versions of the CDF function (such as the analytical version proposed here, potentially 
"https://stackoverflow.com/questions/9242907/how-do-i-generate-normal-cumulative-distribution-in-java-its-inverse-cdf-how")- Implement 
- Implement a version of Black Scholes 76 equation using java streams() (utilising fork/join)
- C++ comparision
- test different java compilers?

Problems I encountered along the way/General Takeaways
- 
- See: https://stackoverflow.com/questions/74011238/understanding-java-17-vector-slowness-and-performance-with-pow-operator?noredirect=1#comment130684999_74011238
- Method size, inlining, and speed (TBD)
- Garbage collection (TBD)
- Method inlining (TBD)
- General takeaways from optimizing JVM (TBD)
