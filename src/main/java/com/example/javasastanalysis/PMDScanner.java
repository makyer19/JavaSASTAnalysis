package com.example.javasastanalysis;

import java.io.IOException;

public class PMDScanner extends ClassloaderScanner {

    /**
     * PMDScanner constructor
     *
     * @param fileManager - A reference to the global FileManager
     * @throws IOException - Throws if JAR files cannot be read
     */
    public PMDScanner(FileManager fileManager) throws IOException {
        super("pmd", fileManager);
        run();
    }
}
