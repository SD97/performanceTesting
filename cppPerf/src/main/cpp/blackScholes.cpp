

#include <iostream>
#include <stdlib.h>
#include "blackScholes.h" //notice double quotes
#include <cassert>
//#include <Eigen/Dense>
//namespaces -
using namespace blackScholes;
using namespace std;

/* From Agnar Fog
Using the library functions in vector code:
Include the header file vectormath_lib.h if you want to use the SVML library. Do not include
vectormath_exp.h, vectormath_trig.h, or vectormath_hyp.h. It is not possible to mix the two kinds of
mathematical functions (inline and library) in the same C++ file. 
*/

/*
I previously tried to do the black Scholes SIMD using SIMD instrinsics such as: _mm256_log_pd (ICC only)
The problem with this is the SIMD intrinsic with log only exists on the intel compiler, and I didn't want to switch + too deep
Currently, GCC only allows The types defined in this manner can be used with a subset of normal C operations.
We can use the following operators on these types: +, -, *, /, unary minus, ^, |, &, ~, %. 
Thus, if I wanted to extend it somehow, I'd hit the earlier problem
*/

/* 
Agner Fog SIMD
*/

// template <class T>
// T* blackScholes::blackScholes::blackScholesVectorAgnostic(T* spot, T* strike, T* interestRate, T* volatility, T* time, int* size) {
//     T callArray[*size];
//     for (int i=0;i<*size;i++)
//     {
//         T vectorArray[5]={*(spot+i), *(strike+i), *(interestRate+i), *(volatility+i), *(time+i)};
//         callArray[i] = calculateSingleCycle(vectorArray);
//     }
//     return callArray;
// }


// template <class T>
// T blackScholes::blackScholes::calculateSingleCycle(T* vectorArray) {
//     //need to check for nullity 
//     T spotElement = *(vectorArray+0);
//     T strikeElement = *(vectorArray+1);
//     T timeElement = *(vectorArray+2);
//     T rateScaled =  *(vectorArray+3)/100.0;
//     T volatilityScaled =  *(vectorArray+4)/100.0;

//     T d1 = ((log(spotElement / strikeElement)
//             + (rateScaled + ((volatilityScaled*volatilityScaled)/2.0))
//             * timeElement)
//             / (volatilityScaled * sqrt(timeElement)));
//     T d2 = d1 - volatilityScaled*sqrt(timeElement);
//     T call = spotElement * customCDF(d1)
//             - strikeElement*exp(-1.0 * rateScaled * timeElement) * customCDF(d2);
//     return call;
// }

// template <class T>
// T customCDF(T value)
// {
//     if constexpr (std::is_same_v<T, Vec4d>) {
//         extern Vec4d erfc(Vec4d); //refers to the external lib
//     }
//     return 0.5 * erfc(-value * M_SQRT1_2);  
// }
