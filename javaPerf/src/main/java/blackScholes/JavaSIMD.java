package blackScholes;

import jdk.incubator.vector.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class JavaSIMD
{
    // Recommended that the species var be static final
    // DoubleVector is a specific subtype with specific mathematical operations
    // Vector<Double> is a more generic version

    private static final  VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final DoubleVector vectorHalf=  DoubleVector.broadcast(SPECIES,0.5);
    DoubleVector vectorOne = DoubleVector.broadcast(SPECIES,1);

    double[] blackScholesVectorized(double[] spotPrices, double[] timeToMaturity, double[] strikePrice,
                                                double[] interestRate, double[] volatility)
    {
        var upperBound = SPECIES.loopBound(spotPrices.length);
        // from the documentation, although we can implement for loops using masks, more performant this way
        double[] callValues = new double[spotPrices.length];

        for (var i=0;i<upperBound; i+= SPECIES.length())
        {
            DoubleVector[] vectorArrays = createVectorizedArrays(spotPrices, timeToMaturity, strikePrice,interestRate,volatility, i);
            calculateBlackScholesSingleCycle(vectorArrays,i,callValues);
        }
        return callValues;
    }

    static DoubleVector[] createVectorizedArrays(double[] spotPrices, double[] timeToMaturity, double[] strikePrice,
                                                  double[] interestRate, double[] volatility, int i)
    {
        DoubleVector vSpot = DoubleVector.fromArray(SPECIES, spotPrices, i);
        DoubleVector vTime = DoubleVector.fromArray(SPECIES, timeToMaturity, i);
        DoubleVector vStrike = DoubleVector.fromArray(SPECIES, strikePrice, i);
        DoubleVector vInterestRate = DoubleVector.fromArray(SPECIES, interestRate, i).div(100);
        DoubleVector vVolatility = DoubleVector.fromArray(SPECIES, volatility, i).div(100);
        DoubleVector[] newArray = {vSpot,vTime,vStrike,vInterestRate,vVolatility};
        return newArray;
    }
    void calculateBlackScholesSingleCycle(DoubleVector[] vectorArrays, int i, double[] callValues){
        // Initialize DoubleVectors from the double array
        //calculate D1
        DoubleVector vTime =vectorArrays[1];
        DoubleVector vVol =vectorArrays[4];
        DoubleVector d1 = calculateD1(vectorArrays);
        //calculate D2
        DoubleVector d2 = d1.sub(vVol.mul(vTime.sqrt()));
        //calculate the final call
        calculateD3(vectorArrays, callValues,i,d1,d2);
    }

    private DoubleVector calculateD1(DoubleVector[] vectorArrays)
    {
        DoubleVector vSpot =vectorArrays[0];
        DoubleVector vTime =vectorArrays[1];
        DoubleVector vStrike =vectorArrays[2];
        DoubleVector vIR =vectorArrays[3];
        DoubleVector vVol =vectorArrays[4];

        return (((vSpot.div(vStrike)).lanewise(VectorOperators.LOG))
                .add((vIR.add((vVol.mul(vVol)).div(2)))
                        .mul(vTime)))
                .div(vVol.mul(vTime.sqrt()));
    }

    private void calculateD3(DoubleVector[] vectorArrays, double[] callValues,int i, DoubleVector d1, DoubleVector d2)
    {
//        System.out.println(Arrays.stream(vectorArrays).sequential().toList().toString());
        DoubleVector vSpot =vectorArrays[0];
        DoubleVector vTime =vectorArrays[1];
        DoubleVector vIR =vectorArrays[3];
        DoubleVector cdfValueD1 = CDFVectorizedExcel(d1);
        DoubleVector cdfValueD2 = CDFVectorizedExcel(d2);
        ((vSpot
                .mul(cdfValueD1))
                .sub(vTime
                        .mul((vIR
                                .mul(vTime)
                                .neg())
                                .lanewise(VectorOperators.EXP))
                        .mul(cdfValueD2))).intoArray(callValues,i);
    }

    private DoubleVector calculateIntermediateValueTwo(@NotNull DoubleVector intermediateValue)
    {
        return((((( intermediateValue.mul(1.330274429).sub(1.821255978))
            .mul(intermediateValue).add(1.781477937))
            .mul(intermediateValue).sub(0.356563782))
            .mul(intermediateValue).add(0.319381530))
            .mul(intermediateValue));
    }

    private DoubleVector calculateExp(DoubleVector absoluteX)
    {
        return ((vectorHalf
                .neg()
                .mul(absoluteX)
                .mul(absoluteX))
                .lanewise(VectorOperators.EXP));
    }
    private DoubleVector calculateIntermediateValueThree(@NotNull DoubleVector dValue, DoubleVector vectorOne, DoubleVector absoluteX, DoubleVector intermediateValueTwo)
    {
        DoubleVector expValue = calculateExp(absoluteX);
        VectorMask<Double> ltZeroMaskltZeroMask = dValue.lt(0.0);
        DoubleVector intermediateValueThree = vectorOne
                .sub(0.398942280401)
                .mul(expValue)
                .mul(intermediateValueTwo);
        DoubleVector potentialAnswer = vectorOne.sub(intermediateValueThree);
        return potentialAnswer.blend(intermediateValueThree, ltZeroMaskltZeroMask);
    }

    public DoubleVector CDFVectorizedExcel(@NotNull DoubleVector dValue)
    {
//        create mask to see if negative
        DoubleVector absoluteX = dValue.abs();
        DoubleVector intermediateValue = vectorOne.div(((absoluteX.mul(0.2316419)).add(1)));
        DoubleVector intermediateValueTwo = calculateIntermediateValueTwo(intermediateValue);
        return calculateIntermediateValueThree(dValue, vectorOne,absoluteX,intermediateValueTwo);
    }




}
