#!/usr/bin/bash

algorithmType=MST_INDEX
datasetType=$2
dataPath=~/mts-subsequence-search/data
outputPath=~/mts-subsequence-search/output
fftConfigPath=~/mts-subsequence-search/configs
queryFromDataset=true
N=$3
maxM=$4
dimensions=$5
qLen=$6
K=1
normalize=$7
nQueries=$8
runtimeMode=FULL_NO_STORE
fftCoveredDistance=-1
queryNoiseEps=0.1
leafSizePercentage=0.0005
experimentId=$9
seed=${10}
parallel=${11}
selectedVariates=${12}
kMeansCluster=${13}
computeOptimalPlan=${14}
segmentMethod=ADHOC
nrSegments=1
missingValueStrategy=VARIANCE
percentageVariatesUsed=1

module load 2022
module load Java/13.0.2

java \
-Xmx128G \
-Djava.util.concurrent.ForkJoinPool.common.parallelism=80 \
-cp MSeg-1.0-jar-with-dependencies.jar \
net/jelter/Main \
$algorithmType \
$datasetType \
$dataPath \
$outputPath \
$fftConfigPath \
$queryFromDataset \
$N \
$maxM \
$dimensions \
$qLen \
$K \
$normalize \
$nQueries \
$runtimeMode \
$fftCoveredDistance \
$queryNoiseEps \
$leafSizePercentage \
$experimentId \
$seed \
$parallel \
$selectedVariates \
$kMeansCluster \
$computeOptimalPlan \
$percentageVariatesUsed \
$segmentMethod \
$nrSegments \
$missingValueStrategy