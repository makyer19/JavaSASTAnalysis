package com.example.javasastanalysis;

import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;

public interface Runner {
    public File doProgram(Part filePart, Part customPart, Part customPartId, Part classPath) throws IOException;
}
