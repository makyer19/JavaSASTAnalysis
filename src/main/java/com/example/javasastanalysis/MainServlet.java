package com.example.javasastanalysis;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@WebServlet(name = "mainServlet", value = "/main-servlet")
@MultipartConfig
@SuppressWarnings("rawtypes")
public class MainServlet extends HttpServlet {
    static HashMap<String, Class> classes;
    private static final Object processLock = new Object();

    public void init() {
        classes = new HashMap<>();
    }

    public MainServlet() {
        //Do nothing
    }
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"output.zip\"");
        Part filePart = request.getPart("file");
        String zipName = filePart.getSubmittedFileName();
        if(zipName.length() > 4 && zipName.substring(zipName.length() - 4).compareTo(".zip") != 0) {
            return;
        }
        Class[] classArg = new Class[2];
        classArg[0] = String.class;
        classArg[1] = String.class;
        Part pmdPart = request.getPart("pmd");
        Part findsecuritybugsPart = request.getPart("findsecuritybugs");
        Part semgrepPart = request.getPart("semgrep");
        Part yascaPart = request.getPart("yasca");
        Part sonarqubePart = request.getPart("sonarqube");
        File[] toOutput = new File[5];
        int numFiles = 0;
        File tempDirectory = new File(System.getProperty("java.io.tmpdir"));
        zipToTemp(tempDirectory, filePart);

        if(pmdPart != null) {
            toOutput[numFiles] = runFromClassLoader("pmd", filePart, tempDirectory, classArg);
            numFiles++;
        }
        if(findsecuritybugsPart != null) {
            toOutput[numFiles] = runFromClassLoader("findsecuritybugs", filePart, tempDirectory, classArg);
            numFiles++;
        }
        if(semgrepPart != null) {
            File outputFile = File.createTempFile("semgrepOutput", ".xml");
            String tempSrcDirectory = tempDirectory.getAbsolutePath();
            String[] dockerCommand = {
                    "semgrep",
                    "--config=auto",
                    "--junit-xml",
                    "--include=*.java",
                    "--dryrun",
                    tempSrcDirectory
            };
            try {
                synchronized (processLock) {
                    Process process = new ProcessBuilder(dockerCommand).redirectOutput(outputFile).start();
                    process.waitFor();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            toOutput[numFiles] = outputFile;
            numFiles++;
        }
        if(yascaPart != null) {
            System.out.println("Starting Yasca");
            File outputFile = File.createTempFile("yascaOutput", ".html");
            String containerName = "yascaContainer";
            System.out.println("Strings were created");
            String[] dockerRunCommand = {
                    "docker",
                    "run",
                    "--rm",
                    "-dit",
                    "--name",
                    containerName,
                    "-v",
                    "scan-dir:/app/toScan",
                    "makyer19/yasca:jsa"
            };
            String[] copyCommand = {
                    "cp",
                    "/usr/local/tomcat/temp/report.html",
                    "/usr/local/tomcat/temp/" + outputFile.getName()
            };
            System.out.println("Docker run command : " + Arrays.toString(dockerRunCommand));
            try {
                synchronized (processLock) {
                    System.out.println("Running Docker");
                    Process runDocker = new ProcessBuilder(dockerRunCommand).start();
                    runDocker.waitFor();
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
                        System.out.println("Docker PS result is: " + result);
                        if (!result.contains(containerName)) {
                            break;
                        }
                    }
                    System.out.println("Copy File");
                    Process copyDocker = new ProcessBuilder(copyCommand).start();
                    copyDocker.waitFor();
                }
            } catch (InterruptedException e) {
                System.out.println("Error");
                e.printStackTrace();
            }
            toOutput[numFiles] = outputFile;
            numFiles++;
        }
        if(sonarqubePart != null) {
            File outputFile = File.createTempFile("sonarqubeOutput", ".json");
            String tempSrcDirectory = tempDirectory.getAbsolutePath();
            String smallerSrcDir = tempSrcDirectory.substring(
                    0,
                    tempSrcDirectory.lastIndexOf(System.getProperty("file.separator"))
            );
            BufferedWriter sonarWriter = new BufferedWriter(new FileWriter(
                    smallerSrcDir +
                            System.getProperty("file.separator") +
                            "sonar-project.properties"
            ));
            sonarWriter.write("sonar.projectKey=jwave-test\nsonar.sources=./temp\n");
            sonarWriter.close();
            String scanMountString = String.format("%s:/usr/src", smallerSrcDir);
            String[] dockerRunCommand = {
                    "docker",
                    "run",
                    "--network=host",
                    "--rm",
                    "-e",
                    "SONAR_HOST_URL=http://localhost:9000",
                    "-e",
                    "SONAR_SCANNER_OPTS=-Dsonar.projectKey=jwave-test -Dsonar.java.binaries=.",
                    "-e",
                    "SONAR_TOKEN=sqp_0f6bfcff3568814c33d517060106ca8419102310",
                    "-v",
                    scanMountString,
                    "sonarsource/sonar-scanner-cli"
            };
            String[] sonarCurlCommand = {
                    "curl",
                    "-u",
                    "admin:jwave_admin",
                    "http://localhost:9000/api/issues/search?types=VULNERABILITY"
            };
            try {
                synchronized (processLock) {
                    ProcessBuilder pb = new ProcessBuilder(dockerRunCommand);
                    pb.redirectErrorStream(true);
                    Process runDocker = pb.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(runDocker.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null)
                        System.out.println("SonarQube Docker: " + line);
                    runDocker.waitFor();
                    Process sonarCurlProcess = new ProcessBuilder(sonarCurlCommand).redirectOutput(outputFile).start();
                    sonarCurlProcess.waitFor();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            toOutput[numFiles] = outputFile;
            numFiles++;
        }
        outputZip(numFiles, toOutput, response);
    }

    private void createClassLoader(String program) throws IOException {
        String targetString = getServletContext().getRealPath(System.getProperty("file.separator"));
        targetString = targetString.substring(0, targetString.indexOf("JavaSASTAnalysis"));
        String pluginString = targetString + String.join(
                System.getProperty("file.separator"),
                Arrays.asList(
                        "JavaSASTAnalysis-1.0-SNAPSHOT",
                        "WEB-INF",
                        "classes",
                        program + "_dependencies"
                )
        );
        System.out.println(pluginString);
        File[] plugins = new File(pluginString).listFiles(file -> file.getName().endsWith(".jar"));
        assert plugins != null;
        List<URL> urls = new ArrayList<>(plugins.length);
        for (File plugin : plugins) {
            urls.add(plugin.toURI().toURL());
        }
        URL[] tempUrls = new URL[urls.size()];
        ClassLoader loader = new URLClassLoader(urls.toArray(tempUrls), this.getClass().getClassLoader());
        Class runnerClass = null;
        try {
            String runnerString = "edu.vt." +
                    program +
                    "runner." +
                    program.substring(0, 1).toUpperCase() +
                    program.substring(1) +
                    "Runner";
            runnerClass = loader.loadClass(runnerString);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        String classname = program + "_class";
        classes.put(classname, runnerClass);
    }

    private void zipToTemp(File tempDirectory, Part filePart) throws IOException {
        int count = 0;
        String tempSrcDirectory = tempDirectory.getAbsolutePath();
        Files.createDirectories(Paths.get(tempSrcDirectory));
        for(File file: Objects.requireNonNull(tempDirectory.listFiles())) {
            boolean check = file.delete();
            if (!check) {
                throw new FileNotFoundException();
            }
        }
        try(ZipInputStream zipInputStream = new ZipInputStream(filePart.getInputStream())) {
            ZipEntry entry = zipInputStream.getNextEntry();
            OutputStream outputStream;
            int read;
            byte[] bytes = new byte[1024];
            while(entry != null) {
                String fileName = entry.getName();
                int isJava = fileName.substring(fileName.length() - 5).compareTo(".java");
                if(fileName.length() > 5 && (isJava == 0
                        || fileName.substring(fileName.length() - 6).compareTo(".class") == 0)) {
                    count = count + 1;
                    File newInputFile;
                    if(isJava == 0) {
                        newInputFile = File.createTempFile("input", ".java");
                    }
                    else {
                        newInputFile = File.createTempFile("input", ".class");
                    }
                    try {
                        outputStream = new FileOutputStream(newInputFile);
                        while((read = zipInputStream.read(bytes)) != -1) {
                            outputStream.write(bytes, 0 , read);
                        }
                        newInputFile.deleteOnExit();
                        if(isJava == 0) {
                            Files.move(
                                    Paths.get(newInputFile.getAbsolutePath()),
                                    Paths.get(tempSrcDirectory +
                                            System.getProperty("file.separator") +
                                            newInputFile.getName())
                            );
                        }
                        else {
                            Files.move(Paths.get(
                                    newInputFile.getAbsolutePath()),
                                    Paths.get(tempDirectory.getAbsolutePath() +
                                            System.getProperty("file.separator") +
                                            newInputFile.getName())
                            );
                        }
                    }
                    catch (FileNotFoundException fnfe){
                        fnfe.printStackTrace();
                    }
                }
                entry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
        }
        catch(IOException e) {
            System.out.println(e.getMessage());
        }
        System.out.println(count);
    }

    //@SuppressWarnings("unchecked")
    @SuppressWarnings("all")
    private File runFromClassLoader(
            String programName,
            Part filePart,
            File tempDirectory,
            Class[] classArg
    ) throws IOException {
        if(!classes.containsKey(String.format("%s_class", programName))) {
            createClassLoader(programName);
        }
        Class runnerClass = classes.get(String.format("%s_class", programName));
        File outputFile = File.createTempFile(String.format("%sOutput", programName), ".xml");
        String outputFilePath = outputFile.getAbsolutePath();
        try {
            Constructor constructor = runnerClass.getDeclaredConstructor(classArg);
            constructor.newInstance(tempDirectory.getAbsolutePath(), outputFilePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        outputFile.deleteOnExit();
        return outputFile;
    }

    private void outputZip(int numFiles, File[] toOutput, HttpServletResponse response) throws IOException {
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
