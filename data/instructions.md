# Where to find data
The authors of the paper have provided part of the data in the following [link](https://drive.google.com/drive/folders/1Qw0x0p-vLFlmvGln30SmpAQf1CAQUado?usp=sharing).

This Google Drive contains the following datasets:
- Stocks
- Weather
- Synthetic

The datasets from the UEA archive such as DuckDuckGeese can be found on the website of the respective paper: [https://www.timeseriesclassification.com/](http://www.timeseriesclassification.com/aeon-toolkit/Archives/Multivariate2018_ts.zip)
After downloading the data from the above sources, one needs to preprocess it through the guidelines in the next section.

# Running own data
To run the code on your own data, the data should be in the following format:
- The data should be in a CSV file.
- The data should be in a row-major format, meaning that each row is a time series, and the first column should be the label.
- Not all rows have to have the same length; the code will read the data line by line.
- The data should not have any headers.

For example, the data should look like this:
```
Time Series 1,1,2,3,4,5
Time Series 2,2,3,4,5
Time Series 3,3,4,5
```

Then, the code is run on the specific dataset by passing the path to the data file as the `dataPath` parameter in the Main function.
