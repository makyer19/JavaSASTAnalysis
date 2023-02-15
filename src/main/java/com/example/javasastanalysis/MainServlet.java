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
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
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
        String dataDir = System.getProperty("user.dir") + "/data";
        boolean check = new File(dataDir).mkdirs();
        if(!check) {
            System.out.print("Directory already created");
        }
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
        Class[] classArg = new Class[5];
        classArg[0] = Part.class;
        classArg[1] = Part.class;
        classArg[2] = Part.class;
        classArg[3] = String.class;
        classArg[4] = String.class;
        Part pmdPart = request.getPart("pmd");
        Part findsecuritybugsPart = request.getPart("findsecuritybugs");
        Part semgrepPart = request.getPart("semgrep");
        Part yascaPart = request.getPart("yasca");
        File[] toOutput = new File[4];
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
            String workingDir = System.getProperty("user.dir");
            String[] dockerCommand = {"docker", "run", "--platform", "linux/x86_64", "--rm", "-v",
                                      String.format("%s:/src", workingDir), "returntocorp/semgrep",
                                      "semgrep", "--config=auto", "--junit-xml", tempSrcDirectory};
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
            File outputFile = File.createTempFile("yascaOutput", ".html");
            String containerName = "yascaContainer";
            String tempSrcDirectory = tempDirectory.getAbsolutePath();
            String outputMountString = String.format("type=bind,source=%s,target=/app/report.html", outputFile.getAbsolutePath());
            String scanMountString = String.format("%s:/app/toScan", tempSrcDirectory);
            String[] dockerRunCommand = {"docker", "run", "-dit", "--name", containerName, "--mount", outputMountString, "-v", scanMountString, "makyer19/yasca:v1"};
            String[] dockerExecCommand = {"docker", "exec", containerName, "./yasca.sh", "--onlyPlugins,BuiltIn", "--extensionsOnly,java", "/app/toScan"};
            String[] dockerKillCommand = {"docker", "kill", containerName};
            String[] dockerRemoveCommand = {"docker", "rm", containerName};
            try {
                synchronized (processLock) {
                    Process runDocker = new ProcessBuilder(dockerRunCommand).start();
                    runDocker.waitFor();
                    Process execDocker = new ProcessBuilder(dockerExecCommand).start();
                    execDocker.waitFor();
                    Process killDocker = new ProcessBuilder(dockerKillCommand).start();
                    killDocker.waitFor();
                    Process removeDocker = new ProcessBuilder(dockerRemoveCommand).start();
                    removeDocker.waitFor();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            toOutput[numFiles] = outputFile;
            numFiles++;
        }
        outputZip(numFiles, toOutput, response);
    }

    private void createClassLoader(String program) throws MalformedURLException {
        String pluginString = "/Users/alexkyer/IdeaProjects/JavaSASTAnalysis/src/main/webapp/WEB-INF/classes/" + program + "_dependencies";
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
            String runnerString = "edu.vt." + program +"runner." + program.substring(0, 1).toUpperCase() + program.substring(1) + "Runner";
            runnerClass = loader.loadClass(runnerString);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        String classname = program + "_class";
        classes.put(classname, runnerClass);
    }

    private void zipToTemp(File tempDirectory, Part filePart) throws IOException {
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
                            Files.move(Paths.get(newInputFile.getAbsolutePath()), Paths.get(tempSrcDirectory + "/" + newInputFile.getName()));
                        }
                        else {
                            Files.move(Paths.get(newInputFile.getAbsolutePath()), Paths.get(tempDirectory.getAbsolutePath() + "/" + newInputFile.getName()));
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
    }

    @SuppressWarnings("unchecked")
    private File runFromClassLoader(String programName, Part filePart, File tempDirectory, Class[] classArg) throws IOException {
        if(!classes.containsKey(String.format("%s_class", programName))) {
            createClassLoader(programName);
        }
        Class runnerClass = classes.get(String.format("%s_class", programName));
        File outputFile = File.createTempFile(String.format("%sOutput", programName), ".xml");
        String outputFilePath = outputFile.getAbsolutePath();
        try {
            Constructor constructor = runnerClass.getDeclaredConstructor(classArg);
            constructor.newInstance(filePart, null, null, tempDirectory.getAbsolutePath(), outputFilePath);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
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
