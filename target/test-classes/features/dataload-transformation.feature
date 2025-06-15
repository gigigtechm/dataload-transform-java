Feature: Data load and transformation

  Scenario: Validate output CSV file is generated from the input CSVs
    Given the input CSV files are generated in "app/in"
    When the application processes the input files and transforms data into the output file
    Then the output CSV file "PositionReport.csv" in "app/out" should match the expected output
