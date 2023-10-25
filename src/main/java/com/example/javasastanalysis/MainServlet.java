package com.example.javasastanalysis;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


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
    protected static final Logger logger = LogManager.getLogger();

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
            logger.info("Beginning PMD Scan");
            toOutput[numFiles] = runFromClassLoader("pmd", tempDirectory, classArg);
            numFiles++;
        }
        if(findsecuritybugsPart != null) {
            logger.info("Beginning FindSecurityBugs Scan");
            toOutput[numFiles] = runFromClassLoader("findsecuritybugs", tempDirectory, classArg);
            numFiles++;
        }
        if(semgrepPart != null) {
            logger.info("Beginning Semgrep Scan");
            File outputFile = File.createTempFile("semgrepOutput", ".xml");
            String tempSrcDirectory = tempDirectory.getAbsolutePath();
            String[] semgrepCommand = {
                    "semgrep",
                    "--config=auto",
                    "--junit-xml",
                    "--include=*.java",
                    "--dryrun",
                    tempSrcDirectory
            };
            try {
                synchronized (processLock) {
                    Process process = new ProcessBuilder(semgrepCommand).redirectOutput(outputFile).start();
                    process.waitFor();
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
            toOutput[numFiles] = outputFile;
            numFiles++;
        }
        if(yascaPart != null) {
            logger.info("Beginning Yasca Scan");
            File outputFile = File.createTempFile("yascaOutput", ".html");
            String[] dockerRunCommand = {
                    "docker",
                    "run",
                    "-dit",
                    "--rm",
                    "--name",
                    "yascaScanner",
                    "-v",
                    "javasastanalysis_scan-dir:/app/toScan",
                    "makyer19/yasca:jsa"
            };
            String[] copyCommand = {
                    "cp",
                    "/usr/local/tomcat/temp/report.html",
                    "/usr/local/tomcat/temp/" + outputFile.getName()
            };
            try {
                runScanFromDocker(dockerRunCommand, copyCommand, "yasca", outputFile);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            toOutput[numFiles] = outputFile;
            numFiles++;
        }
        if(sonarqubePart != null) {
            logger.info("Checking sonar.config is populated");
            String targetString = getServletContext().getRealPath(System.getProperty("file.separator"));
            targetString = targetString.substring(0, targetString.indexOf("webapps"));
            String pluginString = targetString + String.join(
                    System.getProperty("file.separator"),
                    Arrays.asList("config", "sonar.config")
            );
            Properties prop = new Properties();
            try (FileInputStream fis = new FileInputStream(pluginString)) {
                prop.load(fis);
            } catch (IOException e) {
                logger.info("Error reading sonar.config file");
                logger.error(e.getMessage());
            }
            logger.info("Beginning Sonarqube Scan");
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
            sonarWriter.write("sonar.projectKey=" +
                    prop.getProperty("SONAR_PROJECT_KEY") +
                    "\nsonar.sources=./temp\n"
            );
            sonarWriter.close();
            String[] dockerRunCommand = {
                    "docker",
                    "run",
                    "--rm",
                    "--network=host",
                    "--name",
                    "sonarScanner",
                    "-e",
                    "SONAR_HOST_URL=http://localhost:9000",
                    "-e",
                    "SONAR_SCANNER_OPTS=-Dsonar.projectKey=" +
                            prop.getProperty("SONAR_PROJECT_KEY") +
                            " -Dsonar.java.binaries=.",
                    "-e",
                    "SONAR_TOKEN=" + prop.getProperty("SONAR_PROJECT_TOKEN"),
                    "-v",
                    "javasastanalysis_scan-dir:/usr/src",
                    "sonarsource/sonar-scanner-cli"
            };
            String[] sonarCurlCommand = {
                    "curl",
                    "-u",
                    "admin:" + prop.getProperty("SONAR_PASSWORD"),
                    "http://sonarqube:9000/api/issues/search?types=BUG"
            };
            try {
                runScanFromDocker(dockerRunCommand, sonarCurlCommand, "sonarqube", outputFile);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            toOutput[numFiles] = outputFile;
            numFiles++;
        }
        outputZip(numFiles, toOutput, response);
    }

    /***
     * Takes the input zip file and copies the contents to a temporary directory
     * @param tempDirectory - The temp directory to send the input files to
     * @param filePart - The zip file sent within the file request
     * @throws IOException - Occurs if a file cannot be created or the zip file cannot be opened
     */
    private void zipToTemp(File tempDirectory, Part filePart) throws IOException {
        int count = 0;
        String tempSrcDirectory = tempDirectory.getAbsolutePath();
        Files.createDirectories(Paths.get(tempSrcDirectory));
        logger.info("Clearing out old input files");
        for(File file: Objects.requireNonNull(tempDirectory.listFiles())) {
            boolean check = file.delete();
            if (!check) {
                logger.info(file.getName() + " failed to delete");
            }
        }
        try(ZipInputStream zipInputStream = new ZipInputStream(filePart.getInputStream())) {
            logger.info("Moving current input files to temp directory");
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
                    catch (FileNotFoundException e){
                        logger.error(e.getMessage());
                    }
                }
                entry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
        }
        catch(IOException e) {
            logger.error(e.getMessage());
        }
    }

    /***
     * Outputs each file within the output array to a zip file
     *
     * @param numFiles - The number of files to send to the output
     * @param toOutput - The files to output to the user
     * @param response - The servlet response sent to the user
     * @throws IOException - Thrown if the output file cannot be found
     */
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

    /***
     * If a class loader does not exist for the specified program, this function will create that class loader
     *
     * @param program - The program whose class loader we are creating
     *
     * @throws IOException - Thrown if a file cannot be found to create the class loader
     */
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
            logger.info("Loading " + runnerString + " class");
            runnerClass = loader.loadClass(runnerString);
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage());
        }
        String classname = program + "_class";
        classes.put(classname, runnerClass);
    }

    /***
     * Runs a scan by utilizing a class loader
     *
     * @param programName - The name of the scanner program
     * @param tempDirectory - The path to the temp directory which contains the input files
     * @param classArg - An array of class variables that allows the system to get the constructor of the class loader
     * @return
     * @throws IOException
     */
    @SuppressWarnings("all")
    private File runFromClassLoader(String programName, File tempDirectory, Class[] classArg) throws IOException {
        if(!classes.containsKey(String.format("%s_class", programName))) {
            logger.info(String.format("Creating classloader for %s", programName));
            createClassLoader(programName);
        }
        Class runnerClass = classes.get(String.format("%s_class", programName));
        File outputFile = File.createTempFile(String.format("%sOutput", programName), ".xml");
        String outputFilePath = outputFile.getAbsolutePath();
        try {
            logger.info(String.format("Beginning %s scan", programName));
            Constructor constructor = runnerClass.getDeclaredConstructor(classArg);
            constructor.newInstance(tempDirectory.getAbsolutePath(), outputFilePath);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        outputFile.deleteOnExit();
        return outputFile;
    }

    /***
     * Accepts a docker run command and a command to run after the docker run command.
     *
     * @param dockerRunCommand - A string array of the docker run command and its arguments
     * @param postDockerRunCommand - A string array of commands to execute after the scanner container exits
     * @param programName - The name of the scanner program
     * @param outputFile - The file to output the scan results to
     * @throws IOException - If the ProcessBuilder fails to execute an I/O operation
     * @throws InterruptedException - If the ProcessBuilder is interrupted before finishing
     */
    private void runScanFromDocker(
            String[] dockerRunCommand,
            String[] postDockerRunCommand,
            String programName,
            File outputFile
    ) throws IOException, InterruptedException {
        String containerName = String.format("%sScanner", programName);
        try {
            synchronized (processLock) {
                ProcessBuilder pb = new ProcessBuilder(dockerRunCommand);
                pb.redirectErrorStream(true);
                Process dockerRunProcess =  pb.start();
                dockerRunProcess.waitFor();
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
    }
}
