package blackScholes;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
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

    VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    public void assertValuesInList(double[] actualValues)
    {
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