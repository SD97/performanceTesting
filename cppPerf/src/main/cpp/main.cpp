

#include <iostream>
#include <stdlib.h>
#include "blackScholes.h" //notice double quotes
#include <cassert>
using namespace blackScholes;
using namespace std;

//need a main class when running
int main()
{
    blackScholes::blackScholes calc;
    int state=25600;
    int* sizePtr; //pointer to mem address containing value of state (some int)
    double* spotPtr; //pointers that point to first element of array, for use in black Scholes 
    double* strikePtr;
    double* interestRatePtr;
    double* volatilityPtr;
    double* timePtr;
    sizePtr = &state; //set sizePtr pointer to point to the address of the size. Then, if we deference later on, we can get the specific size value

    calc.setup(state, &spotPtr, &strikePtr, &interestRatePtr, &volatilityPtr, &timePtr, &sizePtr); //we pass in the specifc address (pass by reference) of the POINTER
    double* result = calc.blackScholesVectorAgnostic<Vec4d>(spotPtr, strikePtr, interestRatePtr, volatilityPtr, timePtr, sizePtr);
    // cout << "SHIRAJ";
    // for(int i=0;i<state;i++)
    // {
    //     cout << "\n Result " << i << ": ";
    //     cout << *(result+i);
    // }

    delete[] spotPtr;
    delete[] strikePtr;
    delete[] interestRatePtr;
    delete[] volatilityPtr;
    delete[] timePtr;
    delete[] result;

    // int stateScalar=51200;
    // int* sizePtrScalar; //unitialized pointer - should point to mem address containing value of state (some int)
    // double* spotPtrScalar; //the following are pointers that should point to first element of array, for use in black Scholes function 
    // double* strikePtrScalar;
    // double* interestRatePtrScalar;
    // double* volatilityPtrScalar;
    // double* timePtrScalar;
    // sizePtrScalar = &stateScalar; //set sizePtr pointer to point to the address of the size. Then, if we deference later on, we can get the specific size value
    
    // calc.setup(stateScalar, &spotPtrScalar, &strikePtrScalar, &interestRatePtrScalar, &volatilityPtrScalar, &timePtrScalar, &sizePtrScalar); //we pass in the specifc address (pass by reference) of the POINTER
    // double* resultScalar = calc.blackScholesVectorAgnostic<double>(spotPtrScalar, strikePtrScalar, interestRatePtrScalar, volatilityPtrScalar, timePtrScalar, sizePtrScalar);
    // // for(int i=0;i<stateScalar;i++)
    // // {
    // //     cout << "\n Result " << i << ": ";
    // //     cout << *(resultScalar+i);
    // // }

    // delete[] spotPtrScalar;
    // delete[] strikePtrScalar;
    // delete[] interestRatePtrScalar;
    // delete[] volatilityPtrScalar;
    // delete[] timePtrScalar;
    // delete[] resultScalar;

    cout << "Finished";
    return 0;
}