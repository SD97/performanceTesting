import matplotlib.pyplot as plt
import numpy as np
import csv
from matplotlib.pyplot import figure

def generateLists(filePath):
    TotalList=[]
    ListDict={}
    TestNames=[]
    ArraySizes=[]

    ThroughputYValues=[]
    ThroughputYValuesErrors=[]
    MemAllocationYValuesErrors=[]
    MemAllocationYValues=[]
    GCCountYValues=[]
    GCTimeYValues=[]

    TotalList.append(TestNames)
    ListDict["TestNames"]=0
    TotalList.append(ArraySizes)
    ListDict["ArraySizes"] = 1
    TotalList.append(ThroughputYValues)
    ListDict["ThroughputYValues"] = 2
    TotalList.append(ThroughputYValuesErrors)
    ListDict["ThroughputYValuesErrors"] = 3
    TotalList.append(MemAllocationYValues)
    ListDict["MemAllocationYValues"] = 4
    TotalList.append(MemAllocationYValuesErrors)
    ListDict["MemAllocationYValuesErrors"] = 5
    TotalList.append(GCCountYValues)
    ListDict["GCCountYValues"] = 6
    TotalList.append(GCTimeYValues)
    ListDict["GCTimeYValues"] = 7

    trackValue=0
    with open(filePath, encoding="UTF8") as f:
        csvReader = csv.reader(f)
        for line in csvReader:
            if len(line) >1 and trackValue>0:
                # print(line)
                if ":" not in line[0]:
                    thpt=line[4].split(" ")[0]
                    ThroughputYValues.append(float(thpt))
                    error = line[5].split(" ")
                    ThroughputYValuesErrors.append(float(error[0]))
                    ArraySizes.append(int(line[1]))
                    TestNames.append(f"{line[0]}_{line[1]}")
                if "gc.alloc.rate" in line[0]:
                    mem=line[4].split(" ")[0]
                    MemAllocationYValues.append(float(mem))
                    error = line[5].split(" ")
                    MemAllocationYValuesErrors.append(float(error[0]))
                if "gc.count" in line[0]:
                    if "≈" in line[4]:
                        count = line[4].split(" ")
                        GCCountYValues.append(count[1])
                    else:
                        GCCountYValues.append(line[4])
                if "gc.time" in line[0]:
                    GCTimeYValues.append(line[4])
                while len(GCTimeYValues)<len(GCCountYValues)-1:
                    GCTimeYValues.append("0")
            trackValue+=1
    print(len(TotalList))
    print(len(TestNames))
    print(len(ArraySizes))
    print(len(ThroughputYValues))
    print(len(ThroughputYValuesErrors))
    print(len(MemAllocationYValues))
    print(len(MemAllocationYValuesErrors))
    print(len(GCCountYValues))
    print(len(GCTimeYValues))
    print(ThroughputYValuesErrors)
    return TotalList, ListDict

def generateGraphs(TotalList,ListDict,filePathToSave):
    x=TotalList[ListDict["ArraySizes"]]
    y=TotalList[ListDict["ThroughputYValues"]]
    err=TotalList[ListDict["ThroughputYValuesErrors"]]
    labels = TotalList[ListDict["TestNames"]]
    colors = np.random.rand(36)
    j=0
    k=3

    figure(figsize=(15, 15), dpi=500)
    plt.title("Java Vectorization Performance Comparisions over Increasing Array Sizes")
    while j<len(x):
        # plt.plot(x[j:j+k], y[j:j+k], label=labels[j:j+k])
        # plt.yticks(np.arange(0, max(y), 1000))
        plt.xticks(np.arange(0, max(x), 50000))
        plt.errorbar(x[j:j+k], y[j:j+k],yerr=err[j:j+k], label=labels[j])
        j+=k
    plt.xticks(rotation='vertical')
    plt.legend(loc=7,prop={'size': 6})
    # plt.subplots_adjust(bottom=0.15)
    # plt.show()
    plt.savefig(f"{filePathToSave}JavaVectorVsScalar")
    plt.yscale("log")
    plt.savefig(f"{filePathToSave}JavaVectorVsScalar_Log")
    # plt.show()

filePath="../data/data_cutdown.csv"
filePathToSave="../data/"
TotalList,ListDict=generateLists(filePath)
generateGraphs(TotalList,ListDict, filePathToSave)