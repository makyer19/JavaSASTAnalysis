package com.example.javasastanalysis;

import java.io.IOException;

public class FindsecuritybugsScanner extends ClassloaderScanner {

    public FindsecuritybugsScanner(FileManager fileManager) throws IOException {
        super("findsecuritybugs", fileManager);
        runFromClassLoader(getProgramName());
    }
}
