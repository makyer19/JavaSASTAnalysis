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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@WebServlet(name = "mainServlet", value = "/main-servlet")
@MultipartConfig
public class MainServlet extends HttpServlet {
    static HashMap<String, Class> classes;

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
        Class[] class_arg = new Class[5];
        class_arg[0] = Part.class;
        class_arg[1] = Part.class;
        class_arg[2] = Part.class;
        class_arg[3] = Part.class;
        class_arg[4] = String.class;
        Part pmdPart = request.getPart("pmd");
        File[] toOutput = new File[1];
        int numFiles = 0;
        if(pmdPart != null) {
            Part pmdClassPath = request.getPart("pmdClassPath");
            if(!classes.containsKey("pmd_class")) {
                createClassLoader("pmd");
            }
            Class runnerClass = classes.get("pmd_class");
            File pmdOutputFile = File.createTempFile("pmdOutput", ".xml");
            String outputFilePath = pmdOutputFile.getAbsolutePath();
            try {
                Constructor pmd_constructor = runnerClass.getDeclaredConstructor(class_arg);
                pmd_constructor.newInstance(filePart, null, null, null, outputFilePath);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
            pmdOutputFile.deleteOnExit();
            toOutput[numFiles] = pmdOutputFile;
            numFiles++;
        }
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

    private void createClassLoader(String program) throws MalformedURLException {
        String pluginString = "/Users/alexkyer/IdeaProjects/JavaSASTAnalysis/src/main/webapp/WEB-INF/classes/" + program + "_dependencies";
        File[] plugins = new File(pluginString).listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith(".jar");
            }
        });
        assert plugins != null;
        List<URL> urls = new ArrayList<>(plugins.length);
        for (File plugin : plugins) {
            urls.add(plugin.toURI().toURL());
        }
        ClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), this.getClass().getClassLoader());
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
}
