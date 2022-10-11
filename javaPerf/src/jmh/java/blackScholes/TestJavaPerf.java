package blackScholes;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

import static blackScholes.Main.createArray;

@State(Scope.Benchmark)
public class TestJavaPerf {
    double[] spotPrices;
    double[] timeToMaturity;
    double[] strikePrice;
    double[] interestRate;
    double[] volatility;
    JavaSIMD javaSIMD;
    JavaScalar javaScalar;
    NormalDistribution normDist = new NormalDistribution();
    int upperBound;

    @Param({"256","25600","256000","2560000","25600000"})
    int arraySize;
    public static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;

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
    }
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testVectorPerformance(Blackhole bh) {
        var upperBound = SPECIES.loopBound(spotPrices.length);
        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            bh.consume(javaSIMD.calculateBlackScholesSingleCycle(spotPrices,timeToMaturity,strikePrice,
                    interestRate,volatility, i));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testScalarPerformance1(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycle(spotPrices,timeToMaturity,strikePrice,
                    interestRate,volatility, i));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void testScalarPerformanceApacheMaths(Blackhole bh) {
        for(int i=0;i<arraySize;i++)
        {
            bh.consume(javaScalar.calculateBlackScholesSingleCycleWithApacheCDF(spotPrices,timeToMaturity,strikePrice,
                    interestRate,volatility, i,normDist));
        }
    }


}