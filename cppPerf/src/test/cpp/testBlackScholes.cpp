#include "blackScholes.h"
#include <cassert>
#include <vectormath_lib.h>
#include <gtest/gtest.h>

// namespaces? + weird ass include

using namespace blackScholes;
using namespace std;

void calculateSingleCycle_VectorTest(){
    blackScholes::blackScholes calculator;
    Vec4d spot = Vec4d(2.0);
    Vec4d strike = Vec4d(3.0);
    Vec4d time =  Vec4d(180);
    Vec4d irScaled = Vec4d(0.03);
    Vec4d volScaled = Vec4d(0.05);
    Vec4d two = Vec4d(2.0);
    Vec4d results = calculator.calculateSingleCycle<Vec4d>(spot, strike, time, irScaled, volScaled, two);
    for (int i=0; i<4;i++)
    {
        // cout << "\n Vector:";
        // cout << results[i];
        EXPECT_EQ(results[i], 1.9996297705877399);
    }
}

void calculateSingleCycle_ScalarTest(){
    blackScholes::blackScholes calculator;
    double spot = 2.0;
    double strike = 3.0;
    double time =  180;
    double irScaled = 0.03;
    double volScaled = 0.05;
    double two = 2.0;
    double results = calculator.calculateSingleCycle<double>(spot, strike, time, irScaled, volScaled, two);
    for (int i=0; i<4;i++)
    {
        // cout << "\n Scalar:";
        // cout << vec4dArray[i];
        EXPECT_EQ(results, 1.9996297705877399);
    }
}

void setupTest_ScalarTest(){
    blackScholes::blackScholes calc;
    int state=8;
    int* sizePtr; //pointer to mem address containing value of state (some int)
    double* spotPtr; //pointers that point to first element of array, for use in black Scholes 
    double* strikePtr;
    double* interestRatePtr;
    double* volatilityPtr;
    double* timePtr;
    sizePtr = &state; //set sizePtr pointer to point to the address of the size. Then, if we deference later on, we can get the specific size value
    //So the above used to 

    calc.setup(state, &spotPtr, &strikePtr, &interestRatePtr, &volatilityPtr, &timePtr, &sizePtr); //we pass in the specifc address (pass by reference) of the POINTER
    double* result =calc.blackScholesVectorAgnostic<double>(spotPtr, strikePtr, interestRatePtr, volatilityPtr, timePtr, sizePtr);
    // cout << " \n \n Setup Test: ";
    // cout << " \n SpotPtr: ";
    // cout << *spotPtr;
    // cout << " \n strikePtr: ";
    // cout << *strikePtr;
    // cout << " \n volatilityPtr: ";
    // cout << *volatilityPtr;
    for (int i=0; i<8;i++)
    {
        // cout << "\n Scalar: ";
        // cout << *(result+i);
        EXPECT_EQ(result[i], 8.6051846908363157);
    }
    delete[] spotPtr;
    delete[] strikePtr;
    delete[] interestRatePtr;
    delete[] volatilityPtr;
    delete[] timePtr;
}

void setupVectorAgnostic_VectorTest(){
    blackScholes::blackScholes calc;
    int state=8;
    int* sizePtr; //pointer to mem address containing value of state (some int)
    double* spotPtr; //pointers that point to first element of array, for use in black Scholes 
    double* strikePtr;
    double* interestRatePtr;
    double* volatilityPtr;
    double* timePtr;
    sizePtr = &state; //set sizePtr pointer to point to the address of the size. Then, if we deference later on, we can get the specific size value
    //So the above used to 

    calc.setup(state, &spotPtr, &strikePtr, &interestRatePtr, &volatilityPtr, &timePtr, &sizePtr); //we pass in the specifc address (pass by reference) of the POINTER
    double* result = calc.blackScholesVectorAgnostic<Vec4d>(spotPtr, strikePtr, interestRatePtr, volatilityPtr, timePtr, sizePtr);
    cout << " \n \n Calc Test:";
    for (int i=0; i<8;i++)
    {
        // cout << "\n Vector: ";
        // cout << *(result+i);
        EXPECT_EQ(result[i], 8.6051846908363139);
    }
    delete[] spotPtr;
    delete[] strikePtr;
    delete[] interestRatePtr;
    delete[] volatilityPtr;
    delete[] timePtr;
    delete[] result;
}

int main() {
    calculateSingleCycle_ScalarTest();
    calculateSingleCycle_VectorTest();
    setupTest_ScalarTest();
    setupVectorAgnostic_VectorTest();
    return 0;
}