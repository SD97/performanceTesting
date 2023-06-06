#include <gtest/gtest.h>
#include <benchmark/benchmark.h>
#include <chrono>
#include <string>
#include <iostream>
#include <stdlib.h>
#include "blackScholes.h"
using namespace blackScholes;
using namespace std;

//// Demonstrate some basic assertions.
//TEST(HelloTest, BasicAssertions) {
//  // Expect two strings not to be equal.
//  EXPECT_STRNE("hello", "world");
//  // Expect equality.
//  EXPECT_EQ(7 * 6, 42);
//}


/* 
For this method, I want to use pointers, and not direct object/variables references
Even though it would simplify things
*/

static void BM_blackScholesScalar(benchmark::State& state) {
  blackScholes::blackScholes calc;
  for (auto _ : state) { //this will be the numbers we set in the range() function below
    // state.PauseTiming();
    int* sizePtr; //unitialized pointer - should point to mem address containing value of state (some int)
    double* spotPtr; //the following are pointers that should point to first element of array, for use in black Scholes function 
    double* strikePtr;
    double* interestRatePtr;
    double* volatilityPtr;
    double* timePtr;
    int size = state.range(0);
    sizePtr = &size; //set sizePtr pointer to point to the address of the size. Then, if we deference later on, we can get the specific size value
    calc.setup(size, &spotPtr, &strikePtr, &interestRatePtr, &volatilityPtr, &timePtr, &sizePtr); //we pass in the specifc address (pass by reference) of the POINTER
    // state.ResumeTiming();
    // This code gets timed
    double* result = calc.blackScholesVectorAgnostic<double>(spotPtr, strikePtr, interestRatePtr, volatilityPtr, timePtr, sizePtr);
    delete[] spotPtr;
    delete[] strikePtr;
    delete[] interestRatePtr;
    delete[] volatilityPtr;
    delete[] timePtr;
    delete[] result;
  }
}


static void BM_blackScholesVector(benchmark::State& state) {
  // Perform setup here
  blackScholes::blackScholes calc;
  for (auto _ : state) { //for each number between 10 and 20 
    // state.PauseTiming();
    int* sizePtr; //pointer to mem address containing value of state (some int)
    double* spotPtr; //pointers that point to first element of array, for use in black Scholes 
    double* strikePtr;
    double* interestRatePtr;
    double* volatilityPtr;
    double* timePtr;
    int size = state.range(0);
    sizePtr = &size; //set sizePtr pointer to point to the address of the size. Then, if we deference later on, we can get the specific size value
    calc.setup(size, &spotPtr, &strikePtr, &interestRatePtr, &volatilityPtr, &timePtr, &sizePtr); //we pass in the specifc address (pass by reference) of the POINTER
    // state.ResumeTiming();
    // This code gets timed
    double* result = calc.blackScholesVectorAgnostic<Vec4d>(spotPtr, strikePtr, interestRatePtr, volatilityPtr, timePtr, sizePtr);
    delete[] spotPtr;
    delete[] strikePtr;
    delete[] interestRatePtr;
    delete[] volatilityPtr;
    delete[] timePtr;
    delete[] result;
  }
}

// Register the function as a benchmark
//Range() allows to specify a RANGE of inputs, and the multiplier allows you to  multiply each one in the range 
// BENCHMARK(BM_SomeFunction)->RangeMultiplier(2)->Range(1 << 10, 1 << 20);
// the << is power to so 1 << 10 is binary math so 2^&10 to 2^20

BENCHMARK(BM_blackScholesVector)->Range(1280, 25600);
BENCHMARK(BM_blackScholesScalar)->Range(1280, 25600);
// Run the benchmark
BENCHMARK_MAIN();