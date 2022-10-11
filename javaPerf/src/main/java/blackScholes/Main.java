package blackScholes;

import jdk.incubator.vector.DoubleVector;

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
        int arraySize=5120000;
        double[] spotPrices = createArray(arraySize);
        double[] timeToMaturity = createArray(arraySize);
        double[] strikePrice = createArray(arraySize);
        double[] interestRate = createArray(arraySize);
        double[] volatility = createArray(arraySize);

        JavaSIMD javaSIMD = new JavaSIMD();
        JavaScalar javaScalar = new JavaScalar();
        var upperBound = javaSIMD.SPECIES.loopBound(spotPrices.length);

        //blackscholes call - scalar
        Long start1 =  System.currentTimeMillis();
        double[] scalarOutput = javaScalar.blackScholesScalar(spotPrices,timeToMaturity,strikePrice, interestRate,volatility);
        Long finish1 =  System.currentTimeMillis();

        //blackscholes call - vectorized
        Long start2 =  System.currentTimeMillis();
        DoubleVector[] vectorOutput = javaSIMD.blackScholesVectorized(spotPrices,timeToMaturity,strikePrice, interestRate,volatility, upperBound);
        Long finish2 =  System.currentTimeMillis();

        //blackscholes call - scalar apache CDF
        Long start3 =  System.currentTimeMillis();
        double[] scalarOutput2 = javaScalar.blackScholesScalarWithApacheCDF(spotPrices,timeToMaturity,strikePrice, interestRate,volatility);
        Long finish3 =  System.currentTimeMillis();

        //print out
        System.out.println("SCALAR TIME (ms):"+ (finish1-start1));
        System.out.println("SCALAR TIME (ms):"+ (finish3-start3));
        System.out.println("VECTOR TIME (ms):"+ (finish2-start2));
        System.out.println("#######FINISHED#######");
    }

}
