# MS-Index: Fast Subsequence Search for Multivariate Time Series under Euclidean Distance

This project implements the MS-Index algorithm as described in the paper titled "MS-Index: Fast Subsequence Search for Multivariate Time Series under Euclidean Distance".

## Running the Code

To run the code, execute the [run.sh]() script. This script sets various parameters and runs the Java program with the specified configuration.
To change the parameters, modify the [run.sh]() script.

```bash
./run.sh
```

## Parameters
The parameters in the `run.sh` script are as follows:
- `algorithmType`: The type of algorithm to use (MSINDEX, BRUTE_FORCE, MASS, ST_INDEX, DSTREE, default: MSINDEX)
- `dataPath`: Path to the data directory (default: data/synthetic)
- `N`: Number of time series to read from the dataset (default: 100)
- `channels`: Number of channels to read for each time series (-1 means all channels, default: -1)
- `nQueryChannels`: Number of query channels to use (-1 means all channels, default: -1)
- `qLen`: Length of the query (default: 1024)
- `maxM`: Maximum number of data points to consider for each time series (default: 100000)
- `K`: Number of nearest neighbors to find (default: 1)
- `normalize`: Whether to consider normalized subsequences or not (default: false)
- `nQueries`: Number of queries to run (default: 100)
- `runtimeMode`: Runtime mode (FULL, FULL_NO_STORE, INDEX, INDEX_NO_STORE, QUERY, default: FULL_NO_STORE)
- `experimentId`: Experiment identifier (default: 1)
- `seed`: Seed for random number generation (default: 1)
- `parallel`: Whether to run in parallel (default: false)
- `queryFromIndexed`: Whether to generate queries from indexed data (default: false)
- `queryNoiseEps`: Level of noise added to queries when generated from indexed data (default: 0.1)

## Data
Instructions on how to get the data from the paper can be found in [data/instructions.md](data/instructions.md).
