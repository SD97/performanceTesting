package blackScholes;

import jdk.incubator.vector.*;

public class JavaSIMD
{
    // Recommended that the species var be static final
    // DoubleVector is a specific subtype with specific mathematical operations
    // Vector<Double> is a more generic version

    public static final  VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    public DoubleVector vectorHalf=  DoubleVector.broadcast(SPECIES,0.5);

    public DoubleVector[] blackScholesVectorized(double[] spotPrices, double[] timeToMaturity, double[] strikePrice,
                                                double[] interestRate, double[] volatility, int upperBound)
    {
        // from the documentation, although we can implement for loops using masks, more performant this way
        DoubleVector[] callValues = new DoubleVector[spotPrices.length];

        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            callValues[i]= calculateBlackScholesSingleCycle(spotPrices,timeToMaturity,strikePrice, interestRate,
                    volatility,i);
        }
        return callValues;
    }

    public DoubleVector calculateBlackScholesSingleCycle(double[] spotPrices, double[] timeToMaturity, double[] strikePrice,
                                                         double[] interestRate, double[] volatility, int i){
        // Initialize DoubleVectors from the double array
//        DoubleVector vectorHundred =  DoubleVector.broadcast(SPECIES,100);
        DoubleVector vSpot = DoubleVector.fromArray(SPECIES, spotPrices, i);
        DoubleVector vTime = DoubleVector.fromArray(SPECIES, timeToMaturity, i);
        DoubleVector vStrike = DoubleVector.fromArray(SPECIES, strikePrice, i);
        DoubleVector vInterestRate = DoubleVector.fromArray(SPECIES, interestRate, i);
        DoubleVector vVolatility = DoubleVector.fromArray(SPECIES, volatility, i);

        DoubleVector vVolScaled =  vVolatility.div(100);
        DoubleVector vRateScaled =  vInterestRate.div(100);
        //calculate D1
        DoubleVector d1 = (((vSpot.div(vStrike)).lanewise(VectorOperators.LOG))
                .add((vRateScaled.add(vectorHalf.mul(vVolScaled.mul(vVolScaled))))
                        .mul(vTime)))
                .div(vVolScaled.mul(vTime.sqrt()));
        //calculate D2
        DoubleVector d2 = d1.sub(vVolScaled.mul(vTime.sqrt()));
        //calculate the final call
        DoubleVector call  = (vSpot
                .mul(CDFVectorizedExcel(d1)))
                .sub(vStrike
                .mul((vRateScaled
                        .mul(vTime)
                        .neg())
                        .lanewise(VectorOperators.EXP))
                .mul(CDFVectorizedExcel(d2)));

        return call;
    }

    public DoubleVector createFinalAnswerUsingMask(DoubleVector intermediateValue, DoubleVector potentialAnswer, VectorMask<Double> ltZeroMaskltZeroMask)
    {
        double[] tempDoubleArray = new double[intermediateValue.length()];
        for (int i = 0; i < tempDoubleArray.length; i++) {
            boolean isSet = ltZeroMaskltZeroMask.laneIsSet(i);
            tempDoubleArray[i] = isSet ?  intermediateValue.lane(i):potentialAnswer.lane(i);
        }
        DoubleVector blendedDoulbeVectorArrayWithCorrectAnswer = DoubleVector.fromArray(SPECIES, tempDoubleArray, 0);
        return blendedDoulbeVectorArrayWithCorrectAnswer;
    }
    public DoubleVector CDFVectorizedExcel(DoubleVector dValue)
    {
        DoubleVector vectorOne = DoubleVector.broadcast(SPECIES,1);
        //create mask to see if negative
        VectorMask<Double> ltZeroMaskltZeroMask = dValue.lt(0.0);

        DoubleVector absoluteX = dValue.abs();
        DoubleVector intermediateValue = vectorOne.div(vectorOne.add(absoluteX.mul(0.2316419)));
        DoubleVector intermediateValueTwo = (((( intermediateValue.mul(1.330274429).sub(1.821255978))
                .mul(intermediateValue).add(1.781477937))
                .mul(intermediateValue).sub(0.356563782))
                .mul(intermediateValue).add(0.319381530))
                .mul(intermediateValue);
        DoubleVector intermediateValueThree = vectorOne
                .sub(0.398942280401)
                .mul((vectorHalf
                        .neg()
                        .mul(absoluteX)
                        .mul(absoluteX))
                        .lanewise(VectorOperators.EXP))
                .mul(intermediateValueTwo);
        //do a blend
        DoubleVector potentialAnswer = vectorOne.sub(intermediateValueThree);
        DoubleVector answer  = createFinalAnswerUsingMask(intermediateValueThree, potentialAnswer,ltZeroMaskltZeroMask);
        return answer;
    }




}
