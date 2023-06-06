#ifndef blackScholes_h
#define blackScholes_h
// #include <stdlib.h>
// #include <string>
// include string in header files, seems putting in cpp file no bueno
#include <iostream>
#include <x86intrin.h>
#include <bits/stdc++.h>
// #include <vectormath_exp.h>
// #include <vectorclass.h>

// #include "../../../version2-2.02.00/vectormath_lib.h"
#include <vectorclass.h>
#include <vectormath_lib.h>
namespace blackScholes
{
    // typedef double v4si __attribute__ (( vector_size(64) )); //this is no longer necessary, only for GCC-style SIMD
    class blackScholes
    {

    public:

        template <class T>
        double* blackScholesVectorAgnostic(double* spot, double* strike, double* interestRate, double* volatility, double* time, int* size) {
            double* callArray = new double[*size]; //we need to allocate memory here, then delete
            T two;
            T spotForCalc;
            T strikeForCalc;
            T irForCalc;
            T volForCalc;
            T timeForCalc;
            int i=0;
            if constexpr (std::is_same_v<T, Vec4d>) {
                two = T(2.0);
                spotForCalc = (T)(*(spot+i), *(spot+i+1), *(spot+i+2),*(spot+i+3));
                strikeForCalc = (T)(*(strike+i), *(strike+i+1), *(strike+i+2),*(strike+i+3));
                irForCalc = (T)(*(interestRate+i), *(interestRate+i+1), *(interestRate+i+2),*(interestRate+i+3));
                volForCalc = (T)(*(volatility+i), *(volatility+i+1), *(volatility+i+2),*(volatility+i+3));
                timeForCalc = (T)(*(time+i), *(time+i+1), *(time+i+2),*(time+i+3));
                while(i < (*size-3)){
                    T resultArray = calculateSingleCycle(spotForCalc, strikeForCalc, irForCalc, volForCalc, timeForCalc, two);
                    resultArray.store(callArray+(i));
                    i+=4;
                }
            }else{
                two = 2.0;
                spotForCalc = *(spot+i);
                strikeForCalc= *(strike+i);
                irForCalc= *(interestRate+i);
                volForCalc = *(volatility+i);
                timeForCalc= *(time+i);
                while(i < (*size)){
                    T resultArray = calculateSingleCycle(spotForCalc, strikeForCalc, irForCalc, volForCalc, timeForCalc, two);
                    // std::cout << "\n Results of Array so Far: " << i << ": ";
                    // int j=0;
                    // while (j <= i)
                    // {
                    //     std::cout << " ";
                    //     std::cout << callArray[j];
                    //     j+=1;
                    // }
                    *(callArray+i) = resultArray;
                    i+=1;
                }
            }
            return callArray;
        }


        template <class T>
        T calculateSingleCycle(T spot, T strike, T rateScaled, T volatilityScaled, T time, T two) {
            //need to check for nullity

            T d1 = ((log(spot / strike)
                    + (rateScaled + ((volatilityScaled*volatilityScaled)/two))
                    * time)
                    / (volatilityScaled * sqrt(time)));
            T d2 = d1 - volatilityScaled * sqrt(time);
            T call = spot * customCDF(d1)
                    - strike*exp(-1.0 * rateScaled * time) * customCDF(d2);
            return call;
        }

        template <class T>
        T customCDF(T value)
        {
            if constexpr (std::is_same_v<T, Vec4d>) {
                extern Vec4d erfc(Vec4d); //refers to the external lib
            }
            return 0.5 * erfc(-value * M_SQRT1_2);  
        }


        /* 
        This uses templates, given the setup for both are identical barring the actual type (double vs Vec4d)

        Normally pointers are passed by value - most languages are pass by value i.e. within a function there is a local memory copy 
        We can pass things by reference, allowing direct manipulation 
        So in the caller function, we passed in the address of the pointer itself
        And in the called function, we have **, meaning this is a pointer to a pointer (which points to a double's address)
        So now there are a couple things we can do:
        1)If we want to play around with the actual value of the original pointer -> **pointer
        2)If we want to assign the orginal pointer to some new pointer -> *pointer

        Note: if we were passing in a basic reference

        Double Note: I need to deal with dangly pointers I think??
        */

        void setup (auto state, double** spotPtr, double** strikePtr, double** interestRatePtr, double** volatilityPtr, double** timePtr,int** sizePtr) {
            int internalState = state;
            // array creation with the size of array being the value of the original pointer 
            // (original pointer now points to the address of size) thus we should get a value such as 128 or 256 here
            // double spotArray = new double[**sizePtr];  //need to do this, otherwise array wont be initd 
            // double strikeArray = new double[**sizePtr]; 
            // double interestRateArray = new double[**sizePtr];
            // double volatilityArray = new double[**sizePtr];
            // double timeArray = new double[**sizePtr];
            // assign pointer to first element of array - can use for pointer arithmetic, and also for black scholes functions
            *spotPtr = new double[**sizePtr];  //need to do this, otherwise array wont be initd 
            *strikePtr = new double[**sizePtr]; 
            *interestRatePtr = new double[**sizePtr]; 
            *volatilityPtr = new double[**sizePtr]; 
            *timePtr = new double[**sizePtr]; 

            // now initialize the array
            for (int i = 0; i < **sizePtr; i += 1) { //checking if i is smaller than the value stored at the sizePtr 
                *(*spotPtr+i)=25.0; //ptr arithmetic: we set the value (using the *) at the address pointed to by the (*spotPtr+1) to SpotElement
                *(*strikePtr+i)=35.0;
                *(*interestRatePtr+i)=0.05;
                *(*volatilityPtr+i)=0.23;
                *(*timePtr+i)=10.0;
            }
            return;
        }

    };

}
#endif
