package blackScholes;

import org.apache.commons.math3.distribution.NormalDistribution;

public class JavaScalar {
    static double[] createScalarArrays(double[] spotPrices, double[] timeToMaturity, double[] strikePrice,
                                                 double[] interestRate, double[] volatility, int i)
    {
        double[] newArray = {spotPrices[i],timeToMaturity[i],strikePrice[i],interestRate[i],volatility[i]};
        return newArray;
    }

    public double[] blackScholesScalar(double[] spotPrices,double[] timeToMaturity,double[] strikePrice,
                                       double[] interestRate,double[] volatility)
    {
        //calculate D1
        //calculate D2
        //calculate the final call
        double[] callValues = new double[spotPrices.length];
        for (int i=0;i<spotPrices.length;i++)
        {
            double[] scalarArrays = createScalarArrays(spotPrices, timeToMaturity, strikePrice, interestRate, volatility, i);
            callValues[i]=calculateBlackScholesSingleCycle(scalarArrays, i);
        }
        return callValues;
    }
    public double calculateBlackScholesSingleCycle(double[] scalarArrays, int i)
    {
//        System.out.println(scalarArrays);
        double volatilityScaled = scalarArrays[4]/100.0;
        double rateScaled =  scalarArrays[3]/100.0;

        double d1 = ((Math.log(scalarArrays[0] / scalarArrays[2])
                + (rateScaled + ((volatilityScaled*volatilityScaled)/2.0))
                * scalarArrays[1])
                / (volatilityScaled * Math.sqrt(scalarArrays[1])));
        double d2 = d1 - volatilityScaled*Math.sqrt(scalarArrays[1]);
        double call = scalarArrays[0] * CNDF(d1)
                - scalarArrays[2]*Math.exp(-1.0 * rateScaled * scalarArrays[1]) * CNDF(d2);
        return call;
    }

    /*
    * Another scalar impl used previously
    * */
    public double[] blackScholesScalarWithApacheCDF(double[] spotPrices,double[] timeToMaturity,double[] strikePrice,
                                       double[] interestRate,double[] volatility)
    {
        //calculate D1
        //calculate D2
        //calculate the final call
        double[] callValues = new double[spotPrices.length];
        NormalDistribution normDist = new NormalDistribution();
        for (int i=0;i<spotPrices.length;i++)
        {
            callValues[i]=calculateBlackScholesSingleCycleWithApacheCDF(spotPrices,timeToMaturity,strikePrice, interestRate,
                    volatility, i, normDist);
        }
        return callValues;
    }

    public double calculateBlackScholesSingleCycleWithApacheCDF(double[] spotPrices,double[] timeToMaturity,double[] strikePrice,
                                                   double[] interestRate,double[] volatility, int i,
                                                   NormalDistribution normDist)
    {
        double volatilityScaled =  volatility[i]/100.0;
        double rateScaled =  interestRate[i]/100.0;

        double d1 = ((Math.log(spotPrices[i] / strikePrice[i])
                + (rateScaled + ((volatilityScaled*volatilityScaled)/2.0))
                * timeToMaturity[i]))
                / (volatilityScaled * Math.sqrt(timeToMaturity[i]));
        double d2 = d1 - volatilityScaled*Math.sqrt(timeToMaturity[i]);
        double call = spotPrices[i] *  normDist.cumulativeProbability(d1)
                - strikePrice[i]*Math.exp(-1.0 * rateScaled * timeToMaturity[i]) * normDist.cumulativeProbability(d2);
        return call;
    }

    double CNDF(double x)
    {
        int neg = (x < 0d) ? 1 : 0;
        if ( neg == 1)
            x *= -1d;

        double k = (1d / ( 1d + 0.2316419 * x));
        double y = (((( 1.330274429 * k - 1.821255978) * k + 1.781477937) * k - 0.356563782) * k + 0.319381530) * k;
        y = 1.0 - 0.398942280401 * Math.exp(-0.5 * x * x) * y;

        return (1d - neg) * y + neg * (1d - y);
    }
}