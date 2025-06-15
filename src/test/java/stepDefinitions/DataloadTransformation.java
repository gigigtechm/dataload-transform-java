package stepDefinitions;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.junit.Assert;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class DataloadTransformation {
    private static final String INPUT_DIR = "app/in";
    private static final String OUTPUT_DIR = "app/out";
    private static final String INSTRUMENT_DETAILS_FILE = "InstrumentDetails.csv";
    private static final String POSITION_DETAILS_FILE = "PositionDetails.csv";
    private static final String OUTPUT_REPORT_FILE = "PositionReport.csv";

    @Given("the input CSV files are generated in {string}")
    public void generateInputCSVFiles(String inputDir) throws IOException {
        // Input directory
        Path inDir = Paths.get(inputDir);
        Files.createDirectories(inDir);

        // Create InstrumentDetails.csv
        String instrumentContent = "ID,Name,ISIN,Unit Price\n" +
                "1,Laptop,ISIN001,750\n";
        Files.write(Paths.get(inputDir, INSTRUMENT_DETAILS_FILE), instrumentContent.getBytes());

        // Create PositionDetails.csv
        String positionContent = "ID,InstrumentID,Quantity\n" +
                "10,1,5\n";
        Files.write(Paths.get(inputDir, POSITION_DETAILS_FILE), positionContent.getBytes());
    }

    @When("the application processes the input files and transforms data into the output file")
    public void transformData() throws Exception {
        // Output directory
        Files.createDirectories(Paths.get(OUTPUT_DIR));

        // Setup an in-memory H2 database
        Class.forName("org.h2.Driver");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:testdb", "sa", "");
                Statement stmt = conn.createStatement()) {

            // Create staging tables
            stmt.execute(
                    "CREATE TABLE stg_InstrumentDetails (ID INT, Name VARCHAR(50), ISIN VARCHAR(50), \"Unit Price\" DECIMAL(10,2))");
            stmt.execute("CREATE TABLE stg_PositionDetails (ID INT, InstrumentID INT, Quantity INT)");

            // Load InstrumentDetails data
            List<String> instrumentLines = Files.readAllLines(Paths.get(INPUT_DIR, INSTRUMENT_DETAILS_FILE));

            for (int i = 1; i < instrumentLines.size(); i++) {
                String[] fields = instrumentLines.get(i).split(",");
                int id = Integer.parseInt(fields[0]);
                String name = fields[1];
                String isin = fields[2];
                double unitPrice = Double.parseDouble(fields[3]);
                String insertSql = String.format("INSERT INTO stg_InstrumentDetails VALUES(%d, '%s', '%s', %f)",
                        id, name, isin, unitPrice);
                stmt.executeUpdate(insertSql);
            }
            // Load PositionDetails data
            List<String> positionLines = Files.readAllLines(Paths.get(INPUT_DIR, POSITION_DETAILS_FILE));
            for (int i = 1; i < positionLines.size(); i++) {
                String[] fields = positionLines.get(i).split(",");
                int id = Integer.parseInt(fields[0]);
                int instrumentId = Integer.parseInt(fields[1]);
                int quantity = Integer.parseInt(fields[2]);
                String insertSql = String.format("INSERT INTO stg_PositionDetails VALUES(%d, %d, %d)", id, instrumentId,
                        quantity);
                stmt.executeUpdate(insertSql);
            }

            // Execute SQL
            String sql = "SELECT " +
                    "ROW_NUMBER() OVER (ORDER BY l.ID) AS ID, " +
                    "l.ID AS PositionId, " +
                    "i.ISIN, " +
                    "l.Quantity, " +
                    "(l.Quantity * i.\"Unit Price\") AS \"Total Price\" " +
                    "FROM stg_PositionDetails l " +
                    "JOIN stg_InstrumentDetails i ON l.instrumentId = i.ID";
            ResultSet rs = stmt.executeQuery(sql);

            // Write the output to a CSV file
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(OUTPUT_DIR, OUTPUT_REPORT_FILE))) {
                writer.write("ID,PositionId,ISIN,Quantity,Total Price");
                writer.newLine();
                while (rs.next()) {
                    int id = rs.getInt("ID");
                    int positionId = rs.getInt("PositionId");
                    String isin = rs.getString("ISIN");
                    int quantity = rs.getInt("Quantity");
                    double totalPrice = rs.getDouble("Total Price");
                    writer.write(String.format("%d,%d,%s,%d,%.1f", id, positionId, isin, quantity, totalPrice));
                    writer.newLine();
                }
            }
            rs.close();
        }

    }

    @Then("the output CSV file {string} in {string} should match the expected output")
    public void validateOutputCSVFile(String outputFile, String outputDir) throws IOException {
        // Expected output content
        String expectedContent = "ID,PositionId,ISIN,Quantity,Total Price\n" +
                "1,10,ISIN001,5,3750.0";

        // Validate generated output file.
        String actualContent = new String(Files.readAllBytes(Paths.get(outputDir, outputFile))).trim();
        Assert.assertEquals(expectedContent.trim(), actualContent.replaceAll("\r\n", "\n").trim());

    }
}