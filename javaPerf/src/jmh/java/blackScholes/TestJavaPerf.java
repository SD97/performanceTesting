package blackScholes;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

import static blackScholes.JavaScalar.createScalarArrays;
import static blackScholes.Main.createArray;

@State(Scope.Benchmark)
public class TestJavaPerf {
    double[] spotPrices;
    double[] timeToMaturity;
    double[] strikePrice;
    double[] interestRate;
    double[] volatility;
    double[] callValues;

    double[][] scalarArrays;
    DoubleVector[][] vectorizedArrays;
    int upperBound;
    JavaSIMD javaSIMD;
    JavaScalar javaScalar;
    NormalDistribution normDist = new NormalDistribution();
    @Param({"25600","256000","2560000"})
    int arraySize;
    public static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;

    @Setup
    public void Setup()
    {
        javaSIMD = new JavaSIMD();
        javaScalar = new JavaScalar();
        spotPrices = createArray(arraySize);
        timeToMaturity = createArray(arraySize);
        strikePrice = createArray(arraySize);
        interestRate = createArray(arraySize);
        volatility = createArray(arraySize);
        upperBound = SPECIES.loopBound(spotPrices.length);
        vectorizedArrays = new DoubleVector[arraySize][];
        scalarArrays = new double[arraySize][];
        int j=0;
        for (var i=0;i<upperBound; i+= SPECIES.length()) {
            vectorizedArrays[j]=JavaSIMD.createVectorizedArrays(spotPrices, timeToMaturity, strikePrice, interestRate, volatility, i);
            j+=1;
        }
        for (var i=0;i<arraySize; i+=1) {
            scalarArrays[i]=createScalarArrays(spotPrices, timeToMaturity, strikePrice, interestRate, volatility, i);
        }
        callValues = new double[spotPrices.length];
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testScalarPerformanceDefault(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i]));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseParallelGC"})
    public void testScalarPerformanceWithParralelGCDefault(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i]));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseParallelGC","-XX:CompileThreshold=25","-XX:InlineSmallCode=200","-XX:MaxInlineSize=150"})
    public void testScalarPerformanceWithParralelGCInlining(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i]));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC"})
    public void testScalarPerformanceWithZGCDefault(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i]));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=25","-XX:InlineSmallCode=200","-XX:MaxInlineSize=150"})
    public void testScalarPerformanceWithZGCInlining(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i]));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testScalarPerformanceNoInline(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i]));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testVectorPerformanceWithDefaultGC(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:CompileThreshold=50","-XX:CompileThreshold=25","-XX:InlineSmallCode=200","-XX:MaxInlineSize=150"})
    public void testVectorPerformanceWithDefaultGCAndInlining(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseParallelGC"})
    public void testVectorPerformanceWithParralelGCDefault(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseParallelGC","-XX:CompileThreshold=25","-XX:InlineSmallCode=200","-XX:MaxInlineSize=150"})
    public void testVectorPerformanceWithParralelGCInlining(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC"})
    public void testVectorPerformanceWithZGCDefault(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=25"})
    public void testVectorPerformanceWithZGCInlining_compileSmaller(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:InlineSmallCode=200"})
    public void testVectorPerformanceWithZGCInlining_inlineSmaller(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:InlineSmallCode=1000"})
    public void testVectorPerformanceWithZGCInlining_inlineSmaller2(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:InlineSmallCode=2500"})
    public void testVectorPerformanceWithZGCInlining_inlineBigger(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:MaxInlineSize=150"})
    public void testVectorPerformanceWithZGCInlining_MaxInlineSizeBigger(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=25","-XX:InlineSmallCode=200"})
    public void testVectorPerformanceWithZGCInlining_compile25Inline200(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=50","-XX:InlineSmallCode=150","-XX:MaxInlineSize=100"})
    public void testVectorPerformanceWithZGCInlining_all_50_150_100(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(jvmArgsAppend = {"-XX:+UseZGC","-XX:CompileThreshold=25","-XX:InlineSmallCode=200","-XX:MaxInlineSize=150"})
    public void testVectorPerformanceWithZGCInlining_all_25_200_150(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }


    /*
    * Unrun benchmarks - for historical purposes/testing
    *
    * */
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void testScalarPerformanceApacheMaths(Blackhole bh) {
//        for(int i=0;i<arraySize;i++)
//        {
//            bh.consume(javaScalar.calculateBlackScholesSingleCycleWithApacheCDF(spotPrices,timeToMaturity,strikePrice,
//                    interestRate,volatility, i,normDist));
//        }
//    }

//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void testCDFPerformanceScalar(Blackhole bh) {
//        for(int i=0;i<arraySize;i++)
//        {
//            bh.consume(javaScalar.CNDF(spotPrices[i]));
//        }
//    }
//    @Benchmark
//    @BenchmarkMode(Mode.Throughput)
//    @OutputTimeUnit(TimeUnit.SECONDS)
//    public void testCDFPerformanceVector(Blackhole bh) {
//        var upperBound = SPECIES.loopBound(spotPrices.length);
//        for (var i=0;i<upperBound; i+= SPECIES.length())
//        {
//            bh.consume(javaSIMD.CDFVectorizedExcel(DoubleVector.fromArray(SPECIES, spotPrices, i)));
//        }
//    }
}