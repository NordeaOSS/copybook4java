/*
 * Copyright (c) 2015. Troels Liebe Bentsen <tlb@nversion.dk>
 * Copyright (c) 2016. Nordea Bank AB
 * Licensed under the MIT license (LICENSE.txt)
 */

package com.nordea.oss.copybook.codegen;

import com.nordea.oss.ByteUtils;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CopyBookConverter {
    private final ScriptEngineManager manager;
    private ScriptEngine engine;
    private final Invocable invocable;
    static Pattern re_className = Pattern.compile("^.*?\\s(?:class|@interface|enum)\\s*([^\\s]+)", Pattern.DOTALL);


    public CopyBookConverter() throws Exception {
        InputStream inputStream = this.getClass().getResourceAsStream("classconverter.html");
        String js = extractJS(inputStream);

        manager = new ScriptEngineManager();
        engine = manager.getEngineByName("nashorn");
        invocable = (Invocable) engine;
        try {
            engine.eval(js);
        }catch (Exception e){
            // In Java 11 nashorn throws null pointer exception.
            // Still, it works as expected. So we can ignore it.
        }
    }

    public void convertFiles(String inputPath, Pattern pattern, String outputPath, String packageRootName, String accessor, String charset,
                             String subClassHandling) throws Exception {
        File inputFile = new File(inputPath);
        inputPath = inputFile.getCanonicalPath();
        List<File> inputFiles = new ArrayList<>();

        if (inputFile.isDirectory()) {
            inputFiles = Files.walk(inputFile.toPath())
                    .filter(p -> pattern.matcher(p.toString()).find() && Files.isRegularFile(p))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

        } else if (inputFile.isFile()) {
            inputFiles.add(inputFile);
        }

        for (File inFile : inputFiles) {
            String packageName = inFile.getCanonicalFile().getParent().substring(inputPath.length()).replace('\\', '.').replace('/', '.').replace('_', '.');
            packageName = packageName.isEmpty() ? packageRootName : packageRootName + "." + packageName.substring(1);
            String rootClassName = getClassNameFromFile(inFile);
            List<String> outClasses = convert(new FileInputStream(inFile), packageName, rootClassName, accessor, charset, subClassHandling, rootClassName);
            for (String outClass : outClasses) {
                Matcher classNameMatcher = re_className.matcher(outClass);
                if (classNameMatcher.find()) {
                    String className = classNameMatcher.group(1);
                    Path outPath = Paths.get(outputPath, packageName.replace('.', '/'), className + ".java");
                    outPath.getParent().toFile().mkdirs();
                    System.out.println("  " + outPath);
                    Files.write(outPath, outClass.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    public List<String> convert(String copybookString, String packageName, String rootClassName, String accessor, String charset, String subClassHandling,
                                String wrapperClassName) throws Exception {
        ScriptObjectMirror results = (ScriptObjectMirror) invocable.invokeFunction("convertCopybook", packageName, rootClassName, copybookString, accessor,
                charset, subClassHandling, wrapperClassName);
        return Arrays.asList(results.values().toArray(new String[results.size()]));
    }

    public List<String> convert(InputStream copybookStream, String packageName, String rootClassName, String accessor, String charset,
                                String subClassHandling, String wrapperClassName) throws Exception {
        return convert(new String(ByteUtils.toByteArray(copybookStream), StandardCharsets.UTF_8), packageName, rootClassName, accessor, charset,
                subClassHandling, wrapperClassName);
    }

    private String extractJS(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        String str;
        boolean capture = false;
        int whiteSpaceOffset = 0;
        while ((str = bufferedReader.readLine()) != null) {
            if (str.indexOf("/*** convertCopybook stop ***/") > 0) {
                capture = false;
            }

            if (capture) {
                builder.append(str.substring(str.length() > whiteSpaceOffset ? whiteSpaceOffset : 0) + "\n");
            }

            if (str.indexOf("/*** convertCopybook start ***/") > 0) {
                while (whiteSpaceOffset < str.length() && Character.isWhitespace(str.charAt(whiteSpaceOffset))) {
                    whiteSpaceOffset++;
                }
                capture = true;
            }
        }
        return builder.toString();
    }

    private String getClassNameFromFile(File file) {
        String className = file.getName();
        int i = className.lastIndexOf('.');
        if (i > 0) {
            className = className.substring(0, i);
        }
        try {
            className = (String) invocable.invokeFunction("toClassName", className);

        } catch (ScriptException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return className;
    }
}
