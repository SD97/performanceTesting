 Performance Testing
-
- This is a java repository designed to compare different implementations of the Black Scholes formula (and other things) in regard to
  performance
- Ultimately, I want to compare implementations of this formula other languages as well (not there yet!)
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
        - Run using: java--enable-preview --add-modules jdk.incubator.vector -jar javaPerf.jar
        - Or run using: gradle run
    - You can run the actual benchmarking as:
        - Run using: java --enable-preview --add-modules jdk.incubator.vector -jar javaPerf-jmh.jar
        - Or run using: gradle jmh
    - And you can enable diagnostic logging with the following::
        - java --enable-preview --add-modules jdk.incubator.vector -XX:+UnlockDiagnosticVMOptions -XX:+PrintIntrinsics -XX:+PrintAssembly -jar javaPerf.jar
        - Or with: gradle run -Pdebug=True
- **Building from source:**
    - Just need JDK17 installd, the gradlew script will download the correct version of gradle for use
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
- Typically, when a processor does a computation, it's executing an instruction (specified within the ISA) on some operators, register values
    - However, it will operate on two values max (X and Y for example)
    - This isn't great if we need to add X1...Xn to Y1...Yn
- Recently, new CPU's are being released that support new vector instructions. These instructions can operate on operands
  that contain multiple data items
    - For example, Zen 4 CPU's supports instructions that can operate on 512 bits
    - So that's 8 doubles at once, given a double is 8 bytes, and each byte is 8 bit
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
    - On WSL2 (5.15.57.1-microsoft-standard-WSL2) andUbuntu 20.04.5 LTS
- All results were obtained on a Ryzen 5800X (has AVX-256 support) with PBO enabled, 3200Mhz RAM (XMP enabled).
- The benchmarks were obtained using JMH utilising the perf, stack, and gc profilers
    - Each benchmark had 5 warmup periods, followed by 5 iterations per fork, and 5 forks per test
    - Runs for 10 seconds per iteration
    - A warmup period of 5 allows appropriate JVM optimizations to be done I believe

### Goals of Testing ###
- Some adhoc testing not documented here showed problems with warmup periods and a potential effect of inlining parameters
- Thus, I decided the first test would be to compare scalar and vector formulas using different GC and parameters
- Going forward, the optimal parameters for the vectorized benchmark will be used
- We tested 2 Black Scholes implementations (Scalar and Vector) over array sizes of 25600,256000 and 2560000 sized arrays
    - with different GC (ZGC, ParellelGC)
    - with and without inlining support
      -  "-XX:CompileThreshold=50","-XX:InlineSmallCode=100" -- all vector tests with different GC's were tested with this
      -  "-XX:CompileThreshold=10","-XX:InlineSmallCode=150" -- only ZGC was tested with this. 
    - with some modified inline parameters "-XX:CompileThreshold=50","-XX:InlineSmallCode=100" - some different parameters

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
    - Online, it seems < 1GB/second is fine. Remember that the rate of CPU's from its cache is VERY fast/high bandwidth,
      and ~25GB for a Zen3 core I believe
- Memory allocation rates are important because the more memory being allocated,the more GC tends to be done
    - GC pauses threads while it collects garbage/not used objects - introducing latency and potentially stutter
    - In order to have millisecond level optimization -> ensure your application is not destroying the GC
    - We can use these to see the effects of different GC's
- IPC and other low level metrics such as cache misses
    - IPC refers to Instructions Per Cycle This is more of a fun metric. Typically, higher IPC given identical frequencies mean
      your program will finish faster, but not if the instructions are say, doing more work (like with SIMD)
    - Really more for general understanding of software and hardware together

Results
-
### Table 1: Overview of Throughput, IPC, and Memory Allocation of the 3 implementations across all array sizes ###
```
Benchmark                                                                                    (arraySize)   Mode  Cnt           Score            Error      Units
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics                                               25600  thrpt   25       1045.795 ±        9.643      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·gc.alloc.rate                                25600  thrpt   25          0.766 ±        0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·gc.count                                     25600  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·ipc                                          25600  thrpt               2.492                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics                                              256000  thrpt   25        109.905 ±        4.412      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·gc.alloc.rate                               256000  thrpt   25          0.762 ±        0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·gc.count                                    256000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·ipc                                         256000  thrpt               2.571                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics                                             2560000  thrpt   25         10.681 ±        0.444      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·gc.alloc.rate                              2560000  thrpt   25          0.760 ±        0.028     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·gc.count                                   2560000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCDifferentInlingMetrics:·ipc                                        2560000  thrpt               2.544                 insns/clk

blackScholes.TestJavaPerf.testScalarPerformance                                                                          25600  thrpt   25       1142.429 ±       22.941      ops/s
blackScholes.TestJavaPerf.testScalarPerformance:·gc.alloc.rate                                                           25600  thrpt   25          0.516 ±        0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance:·gc.count                                                                25600  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformance:·ipc                                                                     25600  thrpt               2.513                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformance                                                                         256000  thrpt   25        114.578 ±        1.428      ops/s
blackScholes.TestJavaPerf.testScalarPerformance:·gc.alloc.rate                                                          256000  thrpt   25          0.515 ±        0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance:·gc.count                                                               256000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformance:·ipc                                                                    256000  thrpt               2.539                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformance                                                                        2560000  thrpt   25         11.593 ±        0.056      ops/s
blackScholes.TestJavaPerf.testScalarPerformance:·gc.alloc.rate                                                         2560000  thrpt   25          0.516 ±        0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance:·gc.count                                                              2560000  thrpt   25            ≈ 0                    counts

blackScholes.TestJavaPerf.testScalarPerformance:·ipc                                                                   2560000  thrpt               2.530                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceNoInline                                                                  25600  thrpt   25       1153.054 ±       21.635      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate                                                   25600  thrpt   25          0.518 ±        0.021     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.count                                                        25600  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·ipc                                                             25600  thrpt               2.514                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceNoInline                                                                 256000  thrpt   25        116.620 ±        0.470      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate                                                  256000  thrpt   25          0.517 ±        0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.count                                                       256000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·ipc                                                            256000  thrpt               2.539                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceNoInline                                                                2560000  thrpt   25         11.622 ±        0.039      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.alloc.rate                                                 2560000  thrpt   25          0.517 ±        0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·gc.count                                                      2560000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceNoInline:·ipc                                                           2560000  thrpt               2.536                 insns/clk

blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC                                                            25600  thrpt   25       1088.090 ±        3.770      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·gc.alloc.rate                                             25600  thrpt   25          0.529 ±        0.021     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·gc.count                                                  25600  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·ipc                                                       25600  thrpt               2.532                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC                                                           256000  thrpt   25        107.960 ±        0.750      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·gc.alloc.rate                                            256000  thrpt   25          0.529 ±        0.021     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·gc.count                                                 256000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·ipc                                                      256000  thrpt               2.535                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC                                                          2560000  thrpt   25         11.536 ±        0.300      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·gc.alloc.rate                                           2560000  thrpt   25          0.525 ±        0.020     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·gc.count                                                2560000  thrpt   25            ≈ 0                    counts

blackScholes.TestJavaPerf.testScalarPerformanceWithParralelGC:·ipc                                                     2560000  thrpt               2.619                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC                                                                   25600  thrpt   25       1052.036 ±        2.648      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·gc.alloc.rate                                                    25600  thrpt   25          0.764 ±        0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·gc.count                                                         25600  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·ipc                                                              25600  thrpt               2.549                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC                                                                  256000  thrpt   25        105.245 ±        0.243      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·gc.alloc.rate                                                   256000  thrpt   25          0.763 ±        0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·gc.count                                                        256000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·ipc                                                             256000  thrpt               2.507                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC                                                                 2560000  thrpt   25         11.009 ±        0.432      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·gc.alloc.rate                                                  2560000  thrpt   25          0.760 ±        0.028     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·gc.count                                                       2560000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGC:·ipc                                                            2560000  thrpt               2.588                 insns/clk

blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges                                               25600  thrpt   25       1168.927 ±       35.066      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·gc.alloc.rate                                25600  thrpt   25          0.750 ±        0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·gc.count                                     25600  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·ipc                                          25600  thrpt               2.545                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges                                              256000  thrpt   25        118.981 ±        0.314      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·gc.alloc.rate                               256000  thrpt   25          0.749 ±        0.029     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·gc.count                                    256000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·ipc                                         256000  thrpt               2.618                 insns/clk
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges                                             2560000  thrpt   25         11.872 ±        0.023      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·gc.alloc.rate                              2560000  thrpt   25          0.749 ±        0.027     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·gc.count                                   2560000  thrpt   25            ≈ 0                    counts
blackScholes.TestJavaPerf.testScalarPerformanceWithZGCWithoutInlineChanges:·ipc                                        2560000  thrpt               2.603                 insns/clk

blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC                                                             25600  thrpt   25       2510.684 ±      730.233      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate                                              25600  thrpt   25       2345.099 ±     2195.388     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.count                                                   25600  thrpt   25         76.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.time                                                    25600  thrpt   25        200.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·ipc                                                        25600  thrpt               2.072                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC                                                            256000  thrpt   25        109.939 ±        1.998      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate                                             256000  thrpt   25       4806.079 ±       87.326     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.count                                                  256000  thrpt   25        155.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.time                                                   256000  thrpt   25        367.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·ipc                                                       256000  thrpt               1.564                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC                                                           2560000  thrpt   25          8.122 ±        0.052      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.alloc.rate                                            2560000  thrpt   25       3551.372 ±       22.598     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.count                                                 2560000  thrpt   25        122.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·gc.time                                                  2560000  thrpt   25        278.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGC:·ipc                                                      2560000  thrpt               1.148                 insns/clk

blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC                                                            25600  thrpt   25       2364.490 ±      114.673      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.alloc.rate                                             25600  thrpt   25       4133.477 ±      769.418     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.count                                                  25600  thrpt   25        132.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.time                                                   25600  thrpt   25         92.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·ipc                                                       25600  thrpt               2.354                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC                                                           256000  thrpt   25        186.619 ±       25.513      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.alloc.rate                                            256000  thrpt   25       3580.567 ±      848.905     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.count                                                 256000  thrpt   25        115.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.time                                                  256000  thrpt   25         93.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·ipc                                                      256000  thrpt               1.911                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC                                                          2560000  thrpt   25         16.379 ±        0.587      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.alloc.rate                                           2560000  thrpt   25       2880.075 ±      588.688     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.count                                                2560000  thrpt   25        100.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·gc.time                                                 2560000  thrpt   25         95.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithParralelGC:·ipc                                                     2560000  thrpt               1.612                 insns/clk

blackScholes.TestJavaPerf.testVectorPerformanceWithZGC                                                                   25600  thrpt   25       2004.570 ±       84.105      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.alloc.rate                                                    25600  thrpt   25       5633.058 ±      707.903     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.count                                                         25600  thrpt   25       2452.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.time                                                          25600  thrpt   25       4236.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·ipc                                                              25600  thrpt               2.448                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC                                                                  256000  thrpt   25        194.313 ±        7.744      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.alloc.rate                                                   256000  thrpt   25       5464.144 ±      702.778     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.count                                                        256000  thrpt   25       1432.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.time                                                         256000  thrpt   25       4250.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·ipc                                                             256000  thrpt               2.414                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC                                                                 2560000  thrpt   25         17.745 ±        0.987      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.alloc.rate                                                  2560000  thrpt   25       4999.227 ±      712.115     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.count                                                       2560000  thrpt   25        449.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·gc.time                                                        2560000  thrpt   25      20258.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGC:·ipc                                                            2560000  thrpt               2.455                 insns/clk

blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges                                               25600  thrpt   25       2155.140 ±      557.390      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.alloc.rate                                25600  thrpt   25       3787.423 ±     2364.083     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.count                                     25600  thrpt   25       1472.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.time                                      25600  thrpt   25       2537.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·ipc                                          25600  thrpt               2.172                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges                                              256000  thrpt   25        151.740 ±        1.470      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.alloc.rate                               256000  thrpt   25       6139.647 ±       59.500     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.count                                    256000  thrpt   25       1532.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.time                                     256000  thrpt   25       4321.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·ipc                                         256000  thrpt               2.137                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges                                             2560000  thrpt   25         13.884 ±        0.460      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.alloc.rate                              2560000  thrpt   25       5615.669 ±      186.193     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.count                                   2560000  thrpt   25        492.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·gc.time                                    2560000  thrpt   25      23238.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCWithoutInlineChanges:·ipc                                        2560000  thrpt               2.222                 insns/clk

blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics                                               25600  thrpt   25       2337.180 ±       14.570      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.alloc.rate                                25600  thrpt   25       3913.452 ±       24.399     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.count                                     25600  thrpt   25       2376.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.time                                      25600  thrpt   25       3965.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·ipc                                          25600  thrpt               2.522                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics                                              256000  thrpt   25        212.639 ±        3.875      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.alloc.rate                               256000  thrpt   25       3560.524 ±       64.871     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.count                                    256000  thrpt   25       1316.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.time                                     256000  thrpt   25       4072.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·ipc                                         256000  thrpt               2.337                 insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics                                             2560000  thrpt   25         19.753 ±        1.263      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.alloc.rate                              2560000  thrpt   25       3892.369 ±      767.786     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.count                                   2560000  thrpt   25        346.000                    counts
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·gc.time                                    2560000  thrpt   25      12477.000                        ms
blackScholes.TestJavaPerf.testVectorPerformanceWithZGCDifferentInlingMetrics:·ipc                                        2560000  thrpt               2.402                 insns/clk

blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining                                         25600  thrpt   25       2319.287 ±        23.531      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate                          25600  thrpt   25       3451.658 ±        35.016     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.count                               25600  thrpt   25        111.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.time                                25600  thrpt   25        291.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·ipc                                    25600  thrpt               2.256                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining                                        256000  thrpt   25        137.140 ±        14.230      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate                         256000  thrpt   25       3563.473 ±       875.394     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.count                              256000  thrpt   25        115.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.time                               256000  thrpt   25        285.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·ipc                                   256000  thrpt               1.572                  insns/clk
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining                                       2560000  thrpt   25         10.004 ±         0.799      ops/s
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.alloc.rate                        2560000  thrpt   25       2026.143 ±       708.325     MB/sec
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.count                             2560000  thrpt   25         70.000                     counts
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·gc.time                              2560000  thrpt   25        164.000                         ms
blackScholes.TestJavaPerf.testVectorPerformanceWithDefaultGCAndInlining:·ipc                                  2560000  thrpt               1.069                  insns/clk
```
### Table 2: Overview of the CPU performance using the Perf profiler (Scalar) - 2560000 sized array (1 iteration example taken) ###
```
          55181.50 msec task-clock:u              #    0.518 CPUs utilized          
                 0      context-switches:u        #    0.000 K/sec                  
                 0      cpu-migrations:u          #    0.000 K/sec                  
             11207      page-faults:u             #    0.203 K/sec                  
      234973244288      cycles:u                  #    4.258 GHz                      (39.30%)
         330224136      stalled-cycles-frontend:u #    0.14% frontend cycles idle     (39.85%)
         318795362      stalled-cycles-backend:u  #    0.14% backend cycles idle      (40.61%)
      610249213935      instructions:u            #    2.60  insn per cycle         
                                                  #    0.00  stalled cycles per insn  (41.01%)
       47372368776      branches:u                #  858.483 M/sec                    (41.46%)
          18333577      branch-misses:u           #    0.04% of all branches          (41.99%)
      160999700435      L1-dcache-loads:u         # 2917.639 M/sec                    (41.81%)
        1543662071      L1-dcache-load-misses:u   #    0.96% of all L1-dcache accesses  (41.20%)
   <not supported>      LLC-loads:u                                                 
   <not supported>      LLC-load-misses:u                                           
         368234600      L1-icache-loads:u         #    6.673 M/sec                    (40.92%)
           2759037      L1-icache-load-misses:u   #    0.75% of all L1-icache accesses  (40.64%)
          38697868      dTLB-loads:u              #    0.701 M/sec                    (40.34%)
          29360530      dTLB-load-misses:u        #   75.87% of all dTLB cache accesses  (40.01%)
           2876460      iTLB-loads:u              #    0.052 M/sec                    (39.77%)
           3192322      iTLB-load-misses:u        #  110.98% of all iTLB cache accesses  (39.64%)
        1563863297      L1-dcache-prefetches:u    #   28.340 M/sec                    (39.23%)
   <not supported>      L1-dcache-prefetch-misses:u    
```
### Table 3: Overview of the CPU performance using the Perf profiler (Vector, ZGC, optimal inlining) - 2560000 sized array (1 iteration example taken)
```
          61336.55 msec task-clock:u              #    0.572 CPUs utilized          
                 0      context-switches:u        #    0.000 K/sec                  
                 0      cpu-migrations:u          #    0.000 K/sec                  
           1045675      page-faults:u             #    0.017 M/sec                  
      252313488866      cycles:u                  #    4.114 GHz                      (39.46%)
         809962321      stalled-cycles-frontend:u #    0.32% frontend cycles idle     (39.75%)
         570132023      stalled-cycles-backend:u  #    0.23% backend cycles idle      (40.09%)
      629635763224      instructions:u            #    2.50  insn per cycle         
                                                  #    0.00  stalled cycles per insn  (40.70%)
       78764171453      branches:u                # 1284.131 M/sec                    (41.36%)
          38430215      branch-misses:u           #    0.05% of all branches          (41.68%)
      267539387470      L1-dcache-loads:u         # 4361.827 M/sec                    (41.38%)
       10014683820      L1-dcache-load-misses:u   #    3.74% of all L1-dcache accesses  (41.18%)
   <not supported>      LLC-loads:u                                                 
   <not supported>      LLC-load-misses:u                                           
        1817934376      L1-icache-loads:u         #   29.639 M/sec                    (41.13%)
           7022123      L1-icache-load-misses:u   #    0.39% of all L1-icache accesses  (40.90%)
         224416031      dTLB-loads:u              #    3.659 M/sec                    (40.85%)
         167763792      dTLB-load-misses:u        #   74.76% of all dTLB cache accesses  (40.53%)
           5098867      iTLB-loads:u              #    0.083 M/sec                    (40.15%)
           7428489      iTLB-load-misses:u        #  145.69% of all iTLB cache accesses  (39.78%)
        9471202351      L1-dcache-prefetches:u    #  154.414 M/sec                    (39.31%)
   <not supported>      L1-dcache-prefetch-misses:u                                   

```

Analysis
-
### Sanity Check That we're using vectorization
- TBD

### Throughput in General
- Despite all the different settings, scalar performance remains roughly the same
- We see that the vector implementation is faster than both scalar implementations across every array size, *unless* heavy garbage collection occurs
  at large array sizes occur
  - On average, we see different vector implementations achieve ~1.6-2.5x faster than the scalar implementation across all array sizes
  - At the smallest array size, we see the vector implementation with the default GC performing ~2.4x better than the scalar version
  - At the largest array size, we see the vector implementation with "-XX:+UseZGC","-XX:CompileThreshold=10","-XX:InlineSmallCode=150"
  
**In general, I think we can see 1.84 to 2.22x speedup for vector black scholes computing arrays between 25,600 and 2.5 million, using inlining parameters and ZGC**
**We can achieve up to a 2.5x perf increase**

- Array sizes
    - We see a non linear decrease in ops/second as we increased array sizes
    - Likely due to GC problems. At high array sizes, the default GC (G1) does not work well
    - Changing GC's to either parallel or ZGC helped
- Modifying inline parameters 
  - A mixed bag, helped certain GC's
- Note: not shown, but we tested the scalar approach with the apache maths CDF function, and it was much slower than regular scalar [not shown here]

### Throughput with Increasing Array Sizes
- The scalar implementation throughput appears to be consistent across all array sizes,
- However, across different GC's, inlining parameters, vector throughput appears to decrease with increasing array size,
  more so than expected
  - G1 GC (no inlie changes): 32% the expected theoretical performance at the highest array size
  - ZGC (no inline changes): 64% of the expected theoretical performance at the highest array size
- There is an effect of the inlining parameters, causing overall performance to drop to 87% and 47% of original
  - However, the initial throughput at the smallest array sizes was also lower.
- Not sure, but this could be due to the increasing GC count and time that happens at higher array sizes

### Throughput with different inline parameters
- There was no effect on scalar code. I likely need to refactor scalar code to be smaller/more agile
- There was a negative effect/minimal effect on the default GC at lower array sizes. 
  - At lower array sizes, we saw a perf decrease of 8%
  - At higher array sizes, we saw a perf improvement of 23%
- Modifying inline parameters has a beneficial effect for the vector implementation with ZGC at all but smallest array sizes
- Making the inline parameters to be less strict ("-XX:CompileThreshold=10","-XX:InlineSmallCode=150")further helped the ZGC vector implementations
```
                        ZGC default | ZGC inline | ZGC inline least strict
  - smallest array size| 100%       |    93%     |  108%
  - largest array size | 100%       |    127%    |  142%
```

### Throughput with different GC
- Currently, by default, the G1 GC is used.
- At the smallest array sizes without inlining, G1 is 25% faster than ZGC
  - However, with equivalent inlining, G1 is 16% faster than ZGC
- At the largest array sizes, without inlining ZGC is 70% faster than G1
  - However, with equivalent inlining, ZGC is 77% faster than G1
- What behavioral differences between them could explain this difference?

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
  - Making inlining le\
  - ss strict seemed to help decrease this

### IPC and other metrics
- IPC of scalar implementations is around 2.5
- However, we have ~2.1-2.5 IPC with vector implemenations
- Instruction count? (TBD)
- Clock speed? (TBD)
- Cache misses? (TBD)

### General Concerns
  - These results were run on a Ryzen 5800X. As a result, I cannot use AVX512, which may limit use of certain instructions
  - I achieved a range of 1.6x-2.5x performance increase, which is below the theoretical limit. 
    - However, the 2.5x is kind of nice, and happens at fairly large arrays tbh
  - JVM does auto-vectorization, so it's not truly scalar vs vector - but the inline test should have fixed that
  - GC slowing down vectorization?
  - Method inlining?
  - Did I benchmark correctly? 
  - Didn't spot check the scalar implementation to optimize it for inlining as did with vector

Future Work
- 
### **Fixes**
- I need to update the equation to take into account dividend yield_
- Improve correctness_
- Add the overflow (tail elements which do not fit) case
- refactor scalar code to be smaller/more agile
### **New Features**
- Implement different versions of the CDF function (such as the analytical version proposed here, potentially 
"https://stackoverflow.com/questions/9242907/how-do-i-generate-normal-cumulative-distribution-in-java-its-inverse-cdf-how") 
- Implement 
- Implement a version of Black Scholes 76 equation using java streams() (utilising fork/join)

Problems I encountered along the way
- 
- See: https://stackoverflow.com/questions/74011238/understanding-java-17-vector-slowness-and-performance-with-pow-operator?noredirect=1#comment130684999_74011238
- Method size, inlining, and speed (TBD)
- Garbage collection (TBD)
  - 