package blackScholes;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
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
    @Param({"2560000"})
//    @Param({"256","25600","256000","2560000","25600000"})
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
    @BenchmarkMode({Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testScalarPerformance(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i], i));
        }
    }
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testVectorPerformance(Blackhole bh) {
        int j =0;
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
            bh.consume(callValues);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testScalarPerformanceNoInline(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(scalarArrays[i], i));
        }
    }

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