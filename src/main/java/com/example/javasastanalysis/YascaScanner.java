package com.example.javasastanalysis;

import java.io.File;
import java.io.IOException;

public class YascaScanner extends DockerScanner {

    /**
     * YascaScanner constructor
     *
     * @param fileManager - A reference to the global FileManager
     * @throws IOException - If the ProcessBuilder fails to execute an I/O operation
     */
    public YascaScanner(FileManager fileManager) throws IOException {
        super("yasca", fileManager, null, null, null);
        File yascaOutputFile = File.createTempFile("yascaOutput", ".html");
        String[] yascaDockerRunCommand = {
                "docker",
                "run",
                "-dit",
                "--rm",
                "--name",
                "yascaScanner",
                "-v",
                "javasastanalysis_scan-dir:/app/toScan",
                "makyer19/yasca:jsa"
        };
        String[] yascaPostCommand = {
                "cp",
                "/usr/local/tomcat/temp/report.html",
                "/usr/local/tomcat/temp/" + yascaOutputFile.getName()
        };
        this.dockerRunCommand = yascaDockerRunCommand;
        this.postDockerRunCommand = yascaPostCommand;
        this.outputFile = yascaOutputFile;
        try {
            run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
