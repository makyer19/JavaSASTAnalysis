package com.example.javasastanalysis;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassloaderScanner extends AbstractScanner {
    private Class[] classArg = new Class[2];

    /**
     * ClassloaderScanner constructor
     *
     * @param programName - The name of the scanner program
     * @param fileManager - A reference to the global FileManager
     */
    public ClassloaderScanner(String programName, FileManager fileManager) {
        super(programName, fileManager);
        classArg[0] = String.class;
        classArg[1] = String.class;
    }

    /***
     * If a class loader does not exist for the specified program, this function will create that class loader
     *
     * @param program - The program whose class loader we are creating
     *
     * @throws IOException - Thrown if a file cannot be found to create the class loader
     */
    private void createClassLoader(String program) throws IOException {
        String targetString = getFileManager().getServletPath();
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
        MainServlet.classes.put(classname, runnerClass);
    }

    /***
     * Runs a scan by utilizing a class loader
     *
     */
    @SuppressWarnings("all")
    @Override
    public void run() throws IOException {
        logStart();
        String programName = getProgramName();
        if(!MainServlet.classes.containsKey(String.format("%s_class", programName))) {
            logger.info(String.format("Creating classloader for %s", programName));
            createClassLoader(programName);
        }
        Class runnerClass = MainServlet.classes.get(String.format("%s_class", programName));
        File outputFile = File.createTempFile(String.format("%sOutput", programName), ".xml");
        String outputFilePath = outputFile.getAbsolutePath();
        try {
            Constructor constructor = runnerClass.getDeclaredConstructor(classArg);
            constructor.newInstance(getFileManager().getSourceDirectory(), outputFilePath);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        outputFile.deleteOnExit();
        logConclude();
        getFileManager().addFileToOutput(outputFile);
    }
}
