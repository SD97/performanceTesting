Performance Testing 
- 
- This is a java repository designed to compare different implementations of the black scholes formula with regards to
performance
- Ultimately, I want to compare implementations of this formula other languages as well (not there yet!)
- Currently, we compare scalar and SIMD implementations of the black scholes formula

Setup
-
  - Note: for debugging please install the hsdis
  - **Running the JAR:**
    - Need java 17
    - The main jar doesn't do anything that important, it simply creates an array, runs the black scholes calculations, and , and does a rough time difference.
      THIS IS *NOT* benchmarking. Just a casual run.
      - java--enable-preview --add-modules jdk.incubator.vector -jar javaPerf.jar
    - You can run the actual benchmarking as:
      - java --enable-preview --add-modules jdk.incubator.vector -jar javaPerf-jmh.jar
    - And you can enable diagnostic logging as 
      - java --enable-preview --add-modules jdk.incubator.vector -XX:+UnlockDiagnosticVMOptions 
      -XX:+PrintIntrinsics -XX:+PrintAssembly -jar javaPerf.jar
  - **Building from source:**
    - To build from source, you must have gradle and java installed
    - gradle build - does as it says and also runs the tests
    - gradle run - creates an array, runs the black scholes calculations, and , and does a rough time difference.
    THIS IS *NOT* benchmarking. Just a casual run. 
    - gradle jmh - this runs actual benchmarks 
    - You can run it with diagnosticVMOptions enabled by passing the -Pdebug=True flag in
      - E.g. gradle run -Pdebug=True, and it'll print out the assembly instructions and intrinsics

Motivations
-
- Both software and hardware have been progressing quickly. Java has moved to its fast release cycles with new features, and wider vector 
instructions are becoming more mainstream in CPUs
- As a result, I became interested in exploring this topic as we can use these tools to create more performant java code.
- More performant code allows us to create better systems utilizing our expensive hardware better, which in turns lowers
costs, and leads to a better user experience (for example if we can calculate options faster)
  
What are SIMD (Single Instruction Multiple Data) Instructions?
-   
- These two links explain it far better than I ever could, but I'll try giving an explanation below:
- Typically, when a processor does a computation, it's executing some instruction (specified within the ISA) on some 
operand
- Recently, new CPU's are being released with new instructions. These instructions can operate on multiple operands
- For example, Zen 4 CPU's supports instructions that can operate on 512 bits 
  - So that's 8 doubles at once, given a double is 8 bytes, and each byte is 8 bit
- This is typically useful in numerical computations such as polynomial evaluation, matrix math, and financial formulas,
where you may have large arrays of data, and you need to iterate over each one
- Thus, you can expect a speedup if you're processing 4 lanes (4 items) or more of the array at once 
- You can also conditionally execute on certain lanes, and not others using a concept known as "masks" (basically
incorporating if/else's into this paradigm)

Methodology
-
- Built using gradle 7 (Gradle 7.5.1) and Java 17 (17.0.4) on WSL2 (5.15.57.1-microsoft-standard-WSL2, Ubuntu distro)
- All results were obtained on a Ryzen 5800X with PBO enabled, 3200Mhz RAM (XMP enabled).
- The benchmarks were obtained using JMH utilising the perf, stack, and gc profilers
    - Each benchmark had 2 warmup periods, followed by 2 iterations per fork, and 2 forks per test
- We tested 3 black scholes implementations (Scalar, ScalarWithApacheCDF, and Vector) over array sizes of 256,25600,256000,2560000 and 25600000 sized arrays
- The Black Scholes equation we wrote here is an implementation of the Black 76 formula for European Options.
    - This is an "an adjustment of his earlier and more famous Black-Scholes options pricing model" 
(Source: https://www.investopedia.com/terms/b/blacksmodel.asp)
    - _To do: I need to update the equation to take into account dividend yield_
- For the scalar equation, I wrote implementations using the cumulative probability from Apache's Math library but also
  a second one using this link: https://stackoverflow.com/questions/442758/which-java-library-computes-the-cumulative-standard-normal-distribution-function
- I did this because I needed a CDF function in my vector Implementation. I decided that this was the easiest first option

Results
- 
- The following results are for black scholes implementations (Scalar, ScalarWithApacheCDF, and Vector) over array sizes 
of 256,25600, 256000, 2560000 and 25600000 sizes

Table 1: Overview of Throughput, IPC, and Memory Allocation of the 3 implementations across all array sizes
--------------------------------------------------
```
Benchmark                                                                                    (arraySize)   Mode  Cnt           Score            Error      Units
blackScholes.TestJavaPerf.testScalarPerformance1                                                     256  thrpt    4      113578.670 ±       2094.141      ops/s
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate                                      256  thrpt    4           0.600 ±          0.013     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate.norm                                 256  thrpt    4           5.817 ±          0.225       B/op
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.count                                           256  thrpt    4           2.000                      counts
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.time                                            256  thrpt    4           5.000                          ms
blackScholes.TestJavaPerf.testScalarPerformance1:·ipc                                                256  thrpt                2.605                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformance1                                                   25600  thrpt    4        1152.334 ±         13.452      ops/s
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate                                    25600  thrpt    4           0.599 ±          0.009     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate.norm                               25600  thrpt    4         572.409 ±         10.466       B/op
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.count                                         25600  thrpt    4           2.000                      counts
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.time                                          25600  thrpt    4           6.000                          ms
blackScholes.TestJavaPerf.testScalarPerformance1:·ipc                                              25600  thrpt                2.615                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformance1                                                  256000  thrpt    4         114.829 ±          1.997      ops/s
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate                                   256000  thrpt    4           0.598 ±          0.007     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate.norm                              256000  thrpt    4        5729.708 ±        105.371       B/op
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.count                                        256000  thrpt    4           2.000                      counts
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.time                                         256000  thrpt    4           4.000                          ms
blackScholes.TestJavaPerf.testScalarPerformance1:·ipc                                             256000  thrpt                2.615                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformance1                                                 2560000  thrpt    4          11.498 ±          0.165      ops/s
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate                                  2560000  thrpt    4           0.598 ±          0.008     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate.norm                             2560000  thrpt    4       57211.550 ±       1646.449       B/op
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.count                                       2560000  thrpt    4           2.000                      counts
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.time                                        2560000  thrpt    4           4.000                          ms
blackScholes.TestJavaPerf.testScalarPerformance1:·ipc                                            2560000  thrpt                2.600                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformance1                                                25600000  thrpt    4           1.137 ±          0.015      ops/s
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate                                 25600000  thrpt    4           0.594 ±          0.001     MB/sec
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.alloc.rate.norm                            25600000  thrpt    4      573467.333 ±       7305.182       B/op
blackScholes.TestJavaPerf.testScalarPerformance1:·gc.count                                      25600000  thrpt    4             ≈ 0                      counts
blackScholes.TestJavaPerf.testScalarPerformance1:·ipc                                           25600000  thrpt                2.546                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths                                           256  thrpt    4       14258.993 ±        113.411      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate                            256  thrpt    4         212.838 ±          1.685     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate.norm                       256  thrpt    4       16435.095 ±          0.406       B/op
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.count                                 256  thrpt    4          63.000                      counts
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.time                                  256  thrpt    4          46.000                          ms
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·ipc                                      256  thrpt                1.535                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths                                         25600  thrpt    4         143.970 ±          1.867      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate                          25600  thrpt    4         214.894 ±          2.771     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate.norm                     25600  thrpt    4     1643457.187 ±         97.255       B/op
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.count                               25600  thrpt    4          62.000                      counts
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.time                                25600  thrpt    4          47.000                          ms
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·ipc                                    25600  thrpt                1.549                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths                                        256000  thrpt    4          14.375 ±          0.421      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate                         256000  thrpt    4         214.611 ±          6.308     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate.norm                    256000  thrpt    4    16433810.731 ±       4225.389       B/op
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.count                              256000  thrpt    4          49.000                      counts
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.time                               256000  thrpt    4          41.000                          ms
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·ipc                                   256000  thrpt                1.539                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths                                       2560000  thrpt    4           1.436 ±          0.010      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate                        2560000  thrpt    4         214.778 ±          1.360     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate.norm                   2560000  thrpt    4   164338276.533 ±       3355.149       B/op
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.count                             2560000  thrpt    4          52.000                      counts
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.time                              2560000  thrpt    4          52.000                          ms
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·ipc                                  2560000  thrpt                1.524                   insns/clk

blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths                                      25600000  thrpt    4           0.141 ±          0.012      ops/s
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate                       25600000  thrpt    4         213.297 ±         16.996     MB/sec
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.alloc.rate.norm                  25600000  thrpt    4  1643248793.000 ±     200104.454       B/op
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.count                            25600000  thrpt    4          16.000                      counts
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·gc.time                             25600000  thrpt    4          28.000                          ms
blackScholes.TestJavaPerf.testScalarPerformanceApacheMaths:·ipc                                 25600000  thrpt                1.520                   insns/clk

blackScholes.TestJavaPerf.testVectorPerformance                                                      256  thrpt    4      167116.295 ±       7417.966      ops/s
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate                                       256  thrpt    4        5284.966 ±        234.486     MB/sec
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate.norm                                  256  thrpt    4       34821.240 ±          0.203       B/op
blackScholes.TestJavaPerf.testVectorPerformance:·gc.count                                            256  thrpt    4         368.000                      counts
blackScholes.TestJavaPerf.testVectorPerformance:·gc.time                                             256  thrpt    4         460.000                          ms
blackScholes.TestJavaPerf.testVectorPerformance:·ipc                                                 256  thrpt                2.305                   insns/clk

blackScholes.TestJavaPerf.testVectorPerformance                                                    25600  thrpt    4        1714.237 ±         82.479      ops/s
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate                                     25600  thrpt    4        5421.257 ±        260.489     MB/sec
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate.norm                                25600  thrpt    4     3482113.967 ±         24.860       B/op
blackScholes.TestJavaPerf.testVectorPerformance:·gc.count                                          25600  thrpt    4         377.000                      counts
blackScholes.TestJavaPerf.testVectorPerformance:·gc.time                                           25600  thrpt    4         476.000                          ms
blackScholes.TestJavaPerf.testVectorPerformance:·ipc                                               25600  thrpt                2.386                   insns/clk

blackScholes.TestJavaPerf.testVectorPerformance                                                   256000  thrpt    4         172.234 ±         24.801      ops/s
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate                                    256000  thrpt    4        5446.997 ±        784.411     MB/sec
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate.norm                               256000  thrpt    4    34821037.967 ±        573.140       B/op
blackScholes.TestJavaPerf.testVectorPerformance:·gc.count                                         256000  thrpt    4         355.000                      counts
blackScholes.TestJavaPerf.testVectorPerformance:·gc.time                                          256000  thrpt    4         452.000                          ms
blackScholes.TestJavaPerf.testVectorPerformance:·ipc                                              256000  thrpt                2.351                   insns/clk

blackScholes.TestJavaPerf.testVectorPerformance                                                  2560000  thrpt    4          16.856 ±          1.342      ops/s
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate                                   2560000  thrpt    4        5331.237 ±        422.649     MB/sec
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate.norm                              2560000  thrpt    4   348212025.850 ±       9355.237       B/op
blackScholes.TestJavaPerf.testVectorPerformance:·gc.count                                        2560000  thrpt    4         370.000                      counts
blackScholes.TestJavaPerf.testVectorPerformance:·gc.time                                         2560000  thrpt    4         473.000                          ms
blackScholes.TestJavaPerf.testVectorPerformance:·ipc                                             2560000  thrpt                2.300                   insns/clk

blackScholes.TestJavaPerf.testVectorPerformance                                                 25600000  thrpt    4           1.694 ±          0.012      ops/s
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate                                  25600000  thrpt    4        5358.064 ±         36.065     MB/sec
blackScholes.TestJavaPerf.testVectorPerformance:·gc.alloc.rate.norm                             25600000  thrpt    4  3482036188.000 ±      38179.237       B/op
blackScholes.TestJavaPerf.testVectorPerformance:·gc.count                                       25600000  thrpt    4         128.000                      counts
blackScholes.TestJavaPerf.testVectorPerformance:·gc.time                                        25600000  thrpt    4         178.000                          ms
blackScholes.TestJavaPerf.testVectorPerformance:·ipc                                            25600000  thrpt                2.316                   insns/clk
```
Table 2: Overview of the CPU performance using the Perf profiler (Scalar) - 256000 sized array (1 iteration example taken)
--------------------------------------------------
          22735.01 msec task-clock:u              #    0.521 CPUs utilized          
                 0      context-switches:u        #    0.000 K/sec                  
                 0      cpu-migrations:u          #    0.000 K/sec                  
              9004      page-faults:u             #    0.396 K/sec                  
       97414746300      cycles:u                  #    4.285 GHz                      (40.47%)
          74938341      stalled-cycles-frontend:u #    0.08% frontend cycles idle     (41.36%)
          99534516      stalled-cycles-backend:u  #    0.10% backend cycles idle      (41.79%)
      148480226128      instructions:u            #    1.52  insn per cycle         
                                                  #    0.00  stalled cycles per insn  (42.34%)
       20816666459      branches:u                #  915.622 M/sec                    (42.67%)
          32116629      branch-misses:u           #    0.15% of all branches          (43.01%)
       47562376024      L1-dcache-loads:u         # 2092.032 M/sec                    (41.92%)
         317240438      L1-dcache-load-misses:u   #    0.67% of all L1-dcache accesses  (41.25%) 
         567004365      L1-icache-loads:u         #   24.940 M/sec                    (40.76%)
           2839402      L1-icache-load-misses:u   #    0.50% of all L1-icache accesses  (39.86%)
          30011897      dTLB-loads:u              #    1.320 M/sec                    (39.67%)
           3588233      dTLB-load-misses:u        #   11.96% of all dTLB cache accesses  (39.34%)
           3612450      iTLB-loads:u              #    0.159 M/sec                    (39.45%)
           1094667      iTLB-load-misses:u        #   30.30% of all iTLB cache accesses  (39.69%)
         172762504      L1-dcache-prefetches:u    #    7.599 M/sec                    (39.72%)

Table 3: Overview of the CPU performance using the Perf profiler (Scalar with Apache maths) - 256000 sized array (1 iteration example taken)
--------------------------------------------------

          22735.01 msec task-clock:u              #    0.521 CPUs utilized          
                 0      context-switches:u        #    0.000 K/sec                  
                 0      cpu-migrations:u          #    0.000 K/sec                  
              9004      page-faults:u             #    0.396 K/sec                  
       97414746300      cycles:u                  #    4.285 GHz                      (40.47%)
          74938341      stalled-cycles-frontend:u #    0.08% frontend cycles idle     (41.36%)
          99534516      stalled-cycles-backend:u  #    0.10% backend cycles idle      (41.79%)
      148480226128      instructions:u            #    1.52  insn per cycle         
                                                  #    0.00  stalled cycles per insn  (42.34%)
       20816666459      branches:u                #  915.622 M/sec                    (42.67%)
          32116629      branch-misses:u           #    0.15% of all branches          (43.01%)
       47562376024      L1-dcache-loads:u         # 2092.032 M/sec                    (41.92%)
         317240438      L1-dcache-load-misses:u   #    0.67% of all L1-dcache accesses  (41.25%) 
         567004365      L1-icache-loads:u         #   24.940 M/sec                    (40.76%)
           2839402      L1-icache-load-misses:u   #    0.50% of all L1-icache accesses  (39.86%)
          30011897      dTLB-loads:u              #    1.320 M/sec                    (39.67%)
           3588233      dTLB-load-misses:u        #   11.96% of all dTLB cache accesses  (39.34%)
           3612450      iTLB-loads:u              #    0.159 M/sec                    (39.45%)
           1094667      iTLB-load-misses:u        #   30.30% of all iTLB cache accesses  (39.69%)
         172762504      L1-dcache-prefetches:u    #    7.599 M/sec                    (39.72%)

Table 4: Overview of the CPU performance using the Perf profiler (Vector)  - 256000 sized array (1 iteration example taken)
--------------------------------------------------

          22605.27 msec task-clock:u              #    0.534 CPUs utilized          
                 0      context-switches:u        #    0.000 K/sec                  
                 0      cpu-migrations:u          #    0.000 K/sec                  
             11210      page-faults:u             #    0.496 K/sec                  
       89401108579      cycles:u                  #    3.955 GHz                      (40.36%)
         217370442      stalled-cycles-frontend:u #    0.24% frontend cycles idle     (41.72%)
         202340204      stalled-cycles-backend:u  #    0.23% backend cycles idle      (42.88%)
      203863173349      instructions:u            #    2.28  insn per cycle         
                                                  #    0.00  stalled cycles per insn  (43.83%)
       24049493330      branches:u                # 1063.889 M/sec                    (44.21%)
          37912060      branch-misses:u           #    0.16% of all branches          (44.93%)
       75072246502      L1-dcache-loads:u         # 3321.007 M/sec                    (43.76%)
        2712585424      L1-dcache-load-misses:u   #    3.61% of all L1-dcache accesses  (42.58%) 
         734224320      L1-icache-loads:u         #   32.480 M/sec                    (41.64%)
           5115763      L1-icache-load-misses:u   #    0.70% of all L1-icache accesses  (40.56%)
          42715155      dTLB-loads:u              #    1.890 M/sec                    (39.98%)
          14381688      dTLB-load-misses:u        #   33.67% of all dTLB cache accesses  (39.27%)
           4527809      iTLB-loads:u              #    0.200 M/sec                    (38.43%)
           1796290      iTLB-load-misses:u        #   39.67% of all iTLB cache accesses  (38.38%)
        2479389093      L1-dcache-prefetches:u    #  109.682 M/sec                    (38.77%)


Analysis
-  
- We see that the vector implementation is faster than both scalar implementations across every array size
  - Seems to scale linearly with array size for vector and scalar1 implementation
  - Vector implementation is roughly only 1.5x faster than the first scalar implementation across all array sizes, but much faster than the
  apache maths scalar implementation
- When we compare IPC across implementations, the first scalar and vector implementation have similar IPC
  - On average they are at ~2.6 and ~2.3
  - However, the apache maths has a lower IPC at 1.5
  - The vector implementation does seem to have similar if slighltly more instructions than the scalar, but apache maths has much less instructions
- Additionally, looking at memory allocation and GC, Vector Black scholes has much higher allocation rate than either scalar implementation
- These results were run on a Ryzen 5800X. As a result, I am only using 256 bit width vectors, allowing for processing up
to 4 doubles at once. For a fixed number of instructions then, and given the same CPU frequency, we would expect the
speedup to be at a theoretical 4x between the vector and scalar1
- However, there are a few reasons why this may not be achieved
  - JVM does autovectorization so it's not truly scalar vs vector
  - There are differences in IPC, clock speed and # of instructons that could play a role
  - GC slowing down vectorization?
  - Are we really using the correct instructions? Fairly certain we are

Future Work
- 
- Add dividends
- Add the overflow (tail elements which do not fit) case
- Increase precision
- Implement different versions of the CDF function (such as the analytical version proposed here
"https://stackoverflow.com/questions/9242907/how-do-i-generate-normal-cumulative-distribution-in-java-its-inverse-cdf-how") 
and compare speeds
- Implement the original black scholes equation
- Implement a version of black scholes 76 equation using java streams() (utilising fork/join)
- Achieve faster than 1.5x speedup 

Problems I encountered along the way
- 
- See: https://stackoverflow.com/questions/74011238/understanding-java-17-vector-slowness-and-performance-with-pow-operator?noredirect=1#comment130684999_74011238 
