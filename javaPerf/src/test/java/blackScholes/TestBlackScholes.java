package blackScholes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class TestBlackScholes {

    JavaScalar javaScalar = new JavaScalar();
    JavaSIMD javaSIMD = new JavaSIMD();
    double[] spotPrices = new double[]{5,10,0.5,3}; //$ //0.325
    double[] timeToMaturity = new double[]{5,10,0.5,3}; //year //0.01
    double[] strikePrice = new double[]{5,10,0.5,0.03}; //$ //0.2
    double[] interestRate = new double[]{5,10,0.5,0.03}; //as a percent //0.325
    double[] volatility = new double[]{5,10,0.5,0.03}; //as a percent //0.325
    double[] expectedValues = new double[]{1.108,6.322,0.001,2.97}; //0.125

    public void assertValuesInList(double[] actualValues)
    {
        System.out.println(Arrays.toString(actualValues));
        for (int i=0;i<expectedValues.length;i++)
        {
            System.out.println(expectedValues[i]);
            System.out.println(actualValues[i]);
            assertEquals(expectedValues[i], (double)Math.round( actualValues[i] * 1000d) / 1000d);
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
        double[] actualValues = javaSIMD.blackScholesVectorized(spotPrices,timeToMaturity,strikePrice,
                interestRate,volatility);
        assertValuesInList(actualValues);
    }
}