package com.example.javasastanalysis;

import java.io.File;
import java.io.IOException;

public class SemgrepScanner extends ProcessScanner {

    /**
     * SemgrepScanner constructor
     *
     * @param fileManager - A reference to the global FileManager
     * @throws IOException - If the ProcessBuilder fails to execute an I/O operation
     */
    public SemgrepScanner(FileManager fileManager) throws IOException {
        super("semgrep", fileManager, null, null);
        String[] semgrepCommand = {
                "semgrep",
                "--config=auto",
                "--junit-xml",
                "--include=*.java",
                "--dryrun",
                getFileManager().getSourceDirectory()
        };
        File outputFile = File.createTempFile(String.format("%sOutput", getProgramName()), ".xml");
        this.processCommand = semgrepCommand;
        this.outputFile = outputFile;
        run();
    }
}
