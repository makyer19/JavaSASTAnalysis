package com.example.javasastanalysis;

import java.io.File;
import java.io.IOException;

public class YascaScanner extends DockerScanner {

    public YascaScanner(FileManager fileManager) throws IOException {
        super("yasca", fileManager);
        runYasca();
    }

    private void runYasca() throws IOException {
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
        try {
            runScanFromDocker(yascaDockerRunCommand, yascaPostCommand, "yasca", yascaOutputFile);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
