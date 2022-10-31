package com.example.javasastanalysis;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@WebServlet(name = "webcatServlet", value = "/webcat-servlet")
@MultipartConfig
public class MainServlet extends HttpServlet {
    static HashMap<String, Class<? extends Runner>> classes;

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
        Part pmdPart = request.getPart("pmd");
        File[] toOutput = new File[3];
        int numFiles = 0;
        if(pmdPart != null) {
            Part pmdClassPath = request.getPart("pmdClassPath");
            if(!classes.containsKey("pmd_class")) {
                createClassLoader("pmd");
            }
            Class<? extends Runner> runnerClass = classes.get("pmd_class");
            File pmdOutputFile = null;
            try {
                pmdOutputFile = runnerClass.getDeclaredConstructor().newInstance().doProgram(filePart, null, null, pmdClassPath);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
            assert pmdOutputFile != null;
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

    private static void createClassLoader(String program) throws MalformedURLException {
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
        ClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
        Class<? extends Runner> runnerClass = null;
        try {
            String tempString = program + "Runner";
            String runnerString = tempString.substring(0, 1).toUpperCase() + tempString.substring(1);
            runnerClass = (Class<? extends Runner>) loader.loadClass(runnerString);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        classes.put("pmd_class", runnerClass);
    }
}
