package com.example.javasastanalysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessScanner extends AbstractScanner {

    public ProcessScanner(String programName, FileManager fileManager) {
        super(programName, fileManager);
    }

    /***
     * Accepts a docker run command and a command to run after the docker run command.
     *
     * @param processCommand - A string array of the command and its arguments
     * @param outputFile - The file to output the scan results to
     * @throws IOException - If the ProcessBuilder fails to execute an I/O operation
     */
    public void runScanFromProcess(String[] processCommand, File outputFile) throws IOException {
        logStart();
        try {
            synchronized (MainServlet.processLock) {
                Process process = new ProcessBuilder(processCommand).redirectOutput(outputFile).start();
                process.waitFor();
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }
        logConclude();
        getFileManager().addFileToOutput(outputFile);
    }
}
