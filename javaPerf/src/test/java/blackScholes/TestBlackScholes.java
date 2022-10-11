package blackScholes;
import jdk.incubator.vector.DoubleVector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class TestBlackScholes {

    JavaScalar javaScalar = new JavaScalar();
    JavaSIMD javaSIMD = new JavaSIMD();
    double[] spotPrices = new double[]{5,10,0.5,0.325}; //$
    double[] timeToMaturity = new double[]{5,10,0.5,0.01}; //year
    double[] strikePrice = new double[]{5,10,0.5,0.2}; //$
    double[] interestRate = new double[]{5,10,0.5,0.325}; //as a percent
    double[] volatility = new double[]{5,10,0.5,0.325}; //as a percent
    double[] expectedValues = new double[]{1.108,6.322,0.001, 0.125};

    public void assertValuesInList(double[] actualValues)
    {
        for (int i=0;i<expectedValues.length;i++)
        {
            System.out.println(expectedValues[i]);
            System.out.println(actualValues[i]);
            assertEquals(expectedValues[i], (double)Math.round( actualValues[i] * 1000d) / 1000d);
        }
    }

    public void assertValuesInList(DoubleVector[] actualValues)
    {
        List<DoubleVector> actualValuesList =  Arrays.stream(actualValues).toList();
        for (int i=0;i<expectedValues.length;i++)
        {
            double entry = actualValuesList.get(0).lane(i);
            System.out.println(expectedValues[i]+" "+entry);
            assertEquals((double)Math.round( expectedValues[i] * 100d) / 100d, (double)Math.round(entry * 100d) / 100d);
        }
    }

    @Test
    public void testScalarBS()
    {
        double[] actualValues = javaScalar.blackScholesScalar(spotPrices,timeToMaturity,strikePrice, interestRate,
                volatility);
        assertValuesInList(actualValues);
    }

    @Test
    public void testScalarBSWithApacheCDF()
    {
        double[] actualValues = javaScalar.blackScholesScalarWithApacheCDF(spotPrices,timeToMaturity,strikePrice, interestRate,
                volatility);
        assertValuesInList(actualValues);
    }

    @Test
    public void testVectorizedBS()
    {
        var upperBound = javaSIMD.SPECIES.loopBound(spotPrices.length);
        DoubleVector[] actualValues = javaSIMD.blackScholesVectorized(spotPrices,timeToMaturity,strikePrice,
                interestRate,volatility,upperBound);
        assertValuesInList(actualValues);
    }
}