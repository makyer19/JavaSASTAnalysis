package com.example.javasastanalysis;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;

public class SonarqubeScanner extends DockerScanner {

    /**
     * SonarqubeScanner constructor
     *
     * @param fileManager - A reference to the global FileManager
     * @throws IOException - If the ProcessBuilder fails to execute an I/O operation
     */
    public SonarqubeScanner(FileManager fileManager) throws IOException {
        super("sonarqube", fileManager, null, null, null);
        Properties prop = setupSonarConfigAndProperties();
        File outputFile = File.createTempFile("sonarqubeOutput", ".json");
        String[] dockerRunCommand = {
                "docker",
                "run",
                "--rm",
                "--network=host",
                "--name",
                "sonarqubeScanner",
                "-e",
                "SONAR_HOST_URL=http://localhost:9000",
                "-e",
                "SONAR_SCANNER_OPTS=-Dsonar.projectKey=" +
                        prop.getProperty("SONAR_PROJECT_KEY") +
                        " -Dsonar.java.binaries=.",
                "-e",
                "SONAR_TOKEN=" + prop.getProperty("SONAR_PROJECT_TOKEN"),
                "-v",
                "javasastanalysis_scan-dir:/usr/src",
                "sonarsource/sonar-scanner-cli"
        };
        String[] sonarCurlCommand = {
                "curl",
                "-u",
                "admin:" + prop.getProperty("SONAR_PASSWORD"),
                "http://sonarqube:9000/api/issues/search?types=VULNERABILITY"
        };
        this.dockerRunCommand = dockerRunCommand;
        this.postDockerRunCommand = sonarCurlCommand;
        this.outputFile = outputFile;
        try {
            run();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Properties setupSonarConfigAndProperties() throws IOException {
        logger.info("Checking sonar.config is populated");
        String targetString = getFileManager().getServletPath();
        targetString = targetString.substring(0, targetString.indexOf("webapps"));
        String pluginString = targetString + String.join(
                System.getProperty("file.separator"),
                Arrays.asList("config", "sonar.config")
        );
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream(pluginString)) {
            prop.load(fis);
        } catch (IOException e) {
            logger.info("Error reading sonar.config file");
            logger.error(e.getMessage());
        }

        String tempSrcDirectory = getFileManager().getSourceDirectory();
        String smallerSrcDir = tempSrcDirectory.substring(
                0,
                tempSrcDirectory.lastIndexOf(System.getProperty("file.separator"))
        );
        BufferedWriter sonarWriter = new BufferedWriter(new FileWriter(
                smallerSrcDir +
                        System.getProperty("file.separator") +
                        "sonar-project.properties"
        ));
        sonarWriter.write("sonar.projectKey=" +
                prop.getProperty("SONAR_PROJECT_KEY") +
                "\nsonar.sources=./temp\nsonar.exclusions=**/*.html\n"
        );
        sonarWriter.close();
        return(prop);
    }
}
