package blackScholes;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;

public class Main {

    public static double[] createArray(int arrayLength)
    {
        double[] array0 = new double[arrayLength];
        for(int i=0;i<arrayLength;i++)
        {
            array0[i] = (i+10.0)/(arrayLength);
        }
        return array0;
    }

    public static void main(String[] args)
    {
        System.out.println("#######STARTING#######");
        //initialize
        int arraySize=25600000;
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
        double[] spotPrices = createArray(arraySize);
        double[] timeToMaturity = createArray(arraySize);
        double[] strikePrice = createArray(arraySize);
        double[] interestRate = createArray(arraySize);
        double[] volatility = createArray(arraySize);

        JavaSIMD javaSIMD = new JavaSIMD();
        JavaScalar javaScalar = new JavaScalar();
        var upperBound = SPECIES.loopBound(spotPrices.length);
        DoubleVector[][] vectorizedArrays = new DoubleVector[arraySize][];

        //blackscholes call - scalar
        Long start1 =  System.currentTimeMillis();
        double[] scalarOutput = javaScalar.blackScholesScalar(spotPrices,timeToMaturity,strikePrice, interestRate,volatility);
        Long finish1 =  System.currentTimeMillis();

        //blackscholes call - vectorized
        Long start2 =  System.currentTimeMillis();
        double[] vectorOutput = javaSIMD.blackScholesVectorized(spotPrices,timeToMaturity,strikePrice, interestRate,volatility);
        Long finish2 =  System.currentTimeMillis();

        double[] callValues = new double[spotPrices.length];
        int j=0;
        for (var i=0;i<upperBound; i+= SPECIES.length()) {
            vectorizedArrays[j]=JavaSIMD.createVectorizedArrays(spotPrices, timeToMaturity, strikePrice, interestRate, volatility, i);
            j+=1;
        }

        Long start3 = System.currentTimeMillis();
        j=0;
        for (var i=0;i<upperBound; i+= SPECIES.length()) {
            javaSIMD.calculateBlackScholesSingleCycle(vectorizedArrays[j], i, callValues);
            j+=1;
        }
        Long finish3 =  System.currentTimeMillis();

//        //blackscholes call - scalar apache CDF
//        Long start4 =  System.currentTimeMillis();
//        double[] scalarOutput2 = javaScalar.blackScholesScalarWithApacheCDF(spotPrices,timeToMaturity,strikePrice, interestRate,volatility);
//        Long finish4 =  System.currentTimeMillis();

        System.out.println("SCALAR TIME (ms): "+ (finish1-start1));
//        System.out.println("SCALAR TIME (ms):"+ (finish4-start4));
        System.out.println("VECTOR TIME (ms): "+ (finish2-start2));
        System.out.println("VECTOR TIME - CALC TIME ONLY (ms): "+ (finish3-start3));
        System.out.println("#######FINISHED#######");
    }

}
