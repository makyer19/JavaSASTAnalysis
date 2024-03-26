package com.example.javasastanalysis;

import java.io.File;
import java.io.IOException;

public class SemgrepScanner extends ProcessScanner {

    public SemgrepScanner(FileManager fileManager) throws IOException {
        super("semgrep", fileManager);
        String[] semgrepCommand = {
                "semgrep",
                "--config=auto",
                "--junit-xml",
                "--include=*.java",
                "--dryrun",
                getFileManager().getSourceDirectory()
        };
        File outputFile = File.createTempFile(String.format("%sOutput", getProgramName()), ".xml");
        runScanFromProcess(semgrepCommand, outputFile);
    }
}
