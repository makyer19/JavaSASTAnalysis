package com.example.javasastanalysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AbstractScanner {
    private static String programName;
    protected static final Logger logger = LogManager.getLogger();
    private final FileManager fileManager;

    public AbstractScanner(String name, FileManager fileManager) {
        programName = name;
        this.fileManager = fileManager;
    }

    public static String getProgramName() {
        return programName;
    }

    public FileManager getFileManager() {
        return this.fileManager;
    }

    public static void logStart() {
        logger.info(String.format(
                "Beginning %s Scan",
                Character.toUpperCase(programName.charAt(0)) + programName.substring(1)
        ));
    }

    public static void logConclude() {
        logger.info(String.format(
                "Concluded %s Scan",
                Character.toUpperCase(programName.charAt(0)) + programName.substring(1)
        ));
    }
}
