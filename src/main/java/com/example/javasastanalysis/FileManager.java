package com.example.javasastanalysis;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileManager {
    private final File[] toOutput;
    private int numFiles;
    private final File sourceDirectory;
    private final String servletPath;

    public FileManager(String servletPath) {
        sourceDirectory = new File(System.getProperty("java.io.tmpdir"));
        numFiles = 0;
        toOutput = new File[5];
        this.servletPath = servletPath;
    }

    public String getSourceDirectory() {
        return this.sourceDirectory.getAbsolutePath();
    }

    public String getServletPath() {
        return this.servletPath;
    }

    public void addFileToOutput(File newFile) {
        toOutput[numFiles] = newFile;
        numFiles++;
    }

    /***
     * Takes the input zip file and copies the contents to a temporary directory
     * @param filePart - The zip file sent within the file request
     * @throws IOException - Occurs if a file cannot be created or the zip file cannot be opened
     */
    public void zipToTemp(Part filePart) throws IOException {
        int count = 0;
        String tempSrcDirectory = sourceDirectory.getAbsolutePath();
        Files.createDirectories(Paths.get(tempSrcDirectory));
        MainServlet.logger.info("Clearing out old input files");
        for(File file: Objects.requireNonNull(sourceDirectory.listFiles())) {
            boolean check = file.delete();
            if (!check) {
                MainServlet.logger.info(file.getName() + " failed to delete");
            }
        }
        try(ZipInputStream zipInputStream = new ZipInputStream(filePart.getInputStream())) {
            MainServlet.logger.info("Moving current input files to temp directory");
            ZipEntry entry = zipInputStream.getNextEntry();
            OutputStream outputStream;
            int read;
            byte[] bytes = new byte[1024];

            while(entry != null) {
                String fileName = entry.getName();
                boolean isJava = fileName.substring(fileName.length() - 5).compareTo(".java") == 0;
                if(fileName.length() > 5 &&
                        (isJava || fileName.substring(fileName.length() - 6).compareTo(".class") == 0) &&
                        !fileName.startsWith("._")
                ) {
                    count = count + 1;
                    File newInputFile;
                    if(isJava) {
                        newInputFile = File.createTempFile(fileName, ".java");
                    }
                    else {
                        newInputFile = File.createTempFile(fileName, ".class");
                    }
                    try {
                        outputStream = new FileOutputStream(newInputFile);
                        while((read = zipInputStream.read(bytes)) != -1) {
                            outputStream.write(bytes, 0 , read);
                        }
                        newInputFile.deleteOnExit();
                        Files.move(
                            Paths.get(newInputFile.getAbsolutePath()),
                            Paths.get(
                                    tempSrcDirectory +
                                    System.getProperty("file.separator") +
                                    newInputFile.getName())
                        );
                    }
                    catch (FileNotFoundException e){
                        MainServlet.logger.error(e.getMessage());
                    }
                }
                entry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
        }
        catch(IOException e) {
            MainServlet.logger.error(e.getMessage());
        }
    }

    /***
     * Outputs each file within the output array to a zip file
     *
     * @param response - The servlet response sent to the user
     * @throws IOException - Thrown if the output file cannot be found
     */
    public void outputZip(HttpServletResponse response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        byte[] bytes = new byte[1024];
        for(int i = 0; i < numFiles; i++) {
            String path = toOutput[i].getAbsolutePath();
            FileInputStream fis = new FileInputStream(path);
            BufferedInputStream bis = new BufferedInputStream(fis);
            zos.putNextEntry(new ZipEntry(toOutput[i].getName()));
            int bytesRead;
            while ((bytesRead = bis.read(bytes)) != -1) {
                zos.write(bytes, 0, bytesRead);
            }
            zos.closeEntry();
        }
        zos.close();
        response.getOutputStream().write(baos.toByteArray());
        response.flushBuffer();
    }
}
