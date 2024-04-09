package com.example.javasastanalysis;

import java.io.IOException;

public class FindsecuritybugsScanner extends ClassloaderScanner {

    /**
     * FindSecurityBugsScanner constructor
     *
     * @param fileManager - A reference to the global FileManager
     * @throws IOException - Throws if JAR files cannot be read
     */
    public FindsecuritybugsScanner(FileManager fileManager) throws IOException {
        super("findsecuritybugs", fileManager);
        run();
    }
}
