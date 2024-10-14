#!/usr/bin/bash

algorithmType=MSINDEX
dataPath=data/synthetic
N=100
channels=-1 # -1 means all channels
nQueryChannels=-1 # -1 means all channels
qLen=1024
maxM=100000
K=1
normalize=false
nQueries=100
runtimeMode=FULL_NO_STORE # FULL, FULL_NO_STORE, INDEX, INDEX_NO_STORE, QUERY
experimentId=1
seed=1
parallel=false

java \
-cp target/MSIndex-1.0-jar-with-dependencies.jar \
io/github/Main \
$algorithmType \
$dataPath \
$N \
$maxM \
$channels \
$nQueryChannels \
$qLen \
$K \
$normalize \
$nQueries \
$runtimeMode \
$experimentId \
$seed \
$parallel