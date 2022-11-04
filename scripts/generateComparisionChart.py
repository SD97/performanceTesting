import matplotlib.pyplot as plt
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
                    TestNames.append(line[0])
                if "gc.alloc.rate" in line[0]:
                    mem=line[4].split(" ")[0]
                    MemAllocationYValues.append(float(mem))
                    error = line[5].split(" ")
                    MemAllocationYValuesErrors.append(float(error[0]))
                if "gc.count" in line[0]:
                    if "≈" in line[4]:
                        count = line[4].split(" ")
                        GCCountYValues.append(float(count[1]))
                    else:
                        GCCountYValues.append(float(line[4]))
                if "gc.time" in line[0]:
                    GCTimeYValues.append(float(line[4]))
                while len(GCTimeYValues)<len(GCCountYValues)-1:
                    GCTimeYValues.append(0)
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

def generateGraphs(TotalList,titleList,yTitleList,fileNameList):
    initialVal=0
    increment=3
    for title,yTitle,fileName in zip(titleList,yTitleList,fileNameList):
        if fileName == "JavaVectorVsScalarThroughput":
            x = TotalList[ListDict["ArraySizes"]]
            y = TotalList[ListDict["ThroughputYValues"]]
            err = TotalList[ListDict["ThroughputYValuesErrors"]]
            labels = TotalList[ListDict["TestNames"]]
            generateActualGraph(xArray=x, yArray=y,errArray=err,labelArray=labels,initialValue=initialVal,incrementValue=increment,
                                title=title, yTitle=yTitle,
                                fileName=fileName, loc=1)
        elif fileName == "JavaVectorVsScalarMemAllocationRate":
            x = TotalList[ListDict["ArraySizes"]]
            y = TotalList[ListDict["MemAllocationYValues"]]
            err = TotalList[ListDict["MemAllocationYValuesErrors"]]
            labels = TotalList[ListDict["TestNames"]]
            generateActualGraph(xArray=x, yArray=y,errArray=err,labelArray=labels,initialValue=initialVal,incrementValue=increment,
                                title=title, yTitle=yTitle,
                                fileName=fileName, loc=7)
        elif fileName == "JavaVectorVsScalarGCCount":
            x = TotalList[ListDict["ArraySizes"]]
            y = TotalList[ListDict["GCCountYValues"]]
            err = [0] * len(x)
            labels = TotalList[ListDict["TestNames"]]
            generateActualGraph(xArray=x, yArray=y,errArray=err,labelArray=labels,initialValue=initialVal,incrementValue=increment,
                                title=title, yTitle=yTitle,
                                fileName=fileName, loc=1)
        elif fileName == "JavaVectorVsScalarGCTime":
            x = TotalList[ListDict["ArraySizes"]]
            y = TotalList[ListDict["GCTimeYValues"]]
            err = [0]*len(x)
            labels = TotalList[ListDict["TestNames"]]
            generateActualGraph(xArray=x, yArray=y,errArray=err,labelArray=labels,initialValue=initialVal,incrementValue=increment,
                                title=title, yTitle=yTitle,
                                fileName=fileName, loc=1)

def generateActualGraph(xArray,yArray,errArray,labelArray, initialValue, incrementValue, title,yTitle, fileName, loc):
    figure(figsize=(15, 15), dpi=500)
    plt.xscale("log")
    plt.yscale("log")
    plt.title(title)
    plt.ylabel(yTitle)
    plt.xlabel('Array Size (log scale)')
    j=initialValue
    k=incrementValue
    while j<len(xArray):
        plt.errorbar(xArray[j:j+k], yArray[j:j+k],yerr=errArray[j:j+k], label=labelArray[j])
        j+=k
    plt.xticks(rotation='vertical')
    plt.legend(loc=loc,prop={'size': 6})
    plt.savefig(f"{filePathToSave}{fileName}")

filePath= "../data/data_31102022_cutdown.csv"
filePathToSave="../data/"
titleList=["Java Vectorization Implementation Throughput over Increasing Array Sizes",
           "Java Vectorization Implementation Mmeory Allocation over Increasing Array Sizes",
           "Java Vectorization Implementation GC Count over Increasing Array Sizes",
           "Java Vectorization Implementation GC Time over Increasing Array Sizes",]
yTitleList=["ops/second", "MB/s","amount", "ms"]
fileNameList=["JavaVectorVsScalarThroughput","JavaVectorVsScalarMemAllocationRate","JavaVectorVsScalarGCCount","JavaVectorVsScalarGCTime"]
TotalList,ListDict=generateLists(filePath)
generateGraphs(TotalList,titleList,yTitleList,fileNameList)
