package stepDefinitions;

public class DataloadTransformation {

    @Given("the input CSV files are generated in {string}")
    public void generateInputCSVFiles(String inputDir) {

    }

    @When("the application processes the input files and transforms data into the output file")
    public void transformData() {

    }

    @Then("the output CSV file {string} in {string} should match the expected output")
    public void validateOutputCSVFile(String outputFile, String outputDir) {

    }
}