package com.example.javasastanalysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class DockerScanner extends AbstractScanner {
    protected String[] dockerRunCommand;
    protected String[] postDockerRunCommand;
    protected File outputFile;

    /**
     * Constructor for DockerScanner
     *
     * @param programName - The name of the scanner program
     * @param fileManager - A reference to the global FileManager
     * @param dockerRunCommand - A string array of the docker run command and its arguments
     * @param postDockerRunCommand - A string array of commands to execute after the scanner container exits
     * @param outputFile - The file to output the scan results to
     */
    public DockerScanner(String programName, FileManager fileManager, String[] dockerRunCommand, String[] postDockerRunCommand, File outputFile) {
        super(programName, fileManager);
        this.dockerRunCommand = dockerRunCommand;
        this.postDockerRunCommand = postDockerRunCommand;
        this.outputFile = outputFile;
    }

    /***
     * Accepts a docker run command and a command to run after the docker run commandd
     *
     * @throws IOException - If the ProcessBuilder fails to execute an I/O operation
     */
    public void run() throws IOException, InterruptedException {
        String programName = getProgramName();
        String containerName = String.format("%sScanner", programName);
        logStart();
        try {
            synchronized (MainServlet.processLock) {
                ProcessBuilder pb = new ProcessBuilder(dockerRunCommand);
                pb.redirectErrorStream(true);
                Process dockerRunProcess =  pb.start();
                if(programName.equals("sonarqube")) {
                    dockerRunProcess.getInputStream().close();
                }
                dockerRunProcess.waitFor();
                TimeUnit.SECONDS.sleep(5);
                while(true) {
                    String[] dockerPsCommand = {
                            "docker",
                            "ps",
                            "-a"
                    };
                    ProcessBuilder psBuilder = new ProcessBuilder(dockerPsCommand);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(psBuilder.start().getInputStream())
                    );
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                        builder.append(System.getProperty("line.separator"));
                    }
                    String result = builder.toString();
                    if (!result.contains(containerName)) {
                        break;
                    }
                }
                if(programName.equals("sonarqube")) {
                    TimeUnit.SECONDS.sleep(180);
                }
                Process postDockerRunProcess = new ProcessBuilder(postDockerRunCommand).redirectOutput(outputFile).start();
                postDockerRunProcess.waitFor();
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
            throw new InterruptedException();
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new IOException();
        }
        logConclude();
        getFileManager().addFileToOutput(outputFile);
    }
}
