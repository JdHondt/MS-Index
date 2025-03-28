#!/usr/bin/bash

algorithmType=MSINDEX # algorithm type: MSINDEX, BRUTE_FORCE, MASS, ST_INDEX, DSTREE, KV_MATCH
dataPath=data/synthetic # path to the data directory
N=100 # number of time series
channels=-1 # -1 means all channels
nQueryChannels=-1 # -1 means all channels
qLen=730 # query length
maxM=100000 # maximum length of the time series
K=1 # number of nearest neighbors
normalize=false # query for normalized subsequences
nQueries=100  # number of queries
runtimeMode=FULL_NO_STORE # FULL, FULL_NO_STORE, INDEX, INDEX_NO_STORE, QUERY
experimentId=1 # experiment id
seed=1 # random seed
parallel=false # run in multi-threaded mode or not
queryFromIndexed=true # query from indexed subsequences or not
queryNoiseEps=0.1 # noise level added to query subsequences

java \
-cp target/MS-Index-1.0-jar-with-dependencies.jar \
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
$parallel \
$queryFromIndexed \
$queryNoiseEps