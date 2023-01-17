/*
 * Copyright (c) 2015. Troels Liebe Bentsen <tlb@nversion.dk>
 * Copyright (c) 2016. Nordea Bank AB
 * Licensed under the MIT license (LICENSE.txt)
 */

package com.nordea.oss.copybook.codegen;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: Fix storing of class files so it is done in memory instead in the root of the project folder

public class JavaSyntaxChecker {
    static JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    static Pattern re_packageName = Pattern.compile("^.*?package\\s*([^\\s;]+)", Pattern.DOTALL);
    static Pattern re_className = Pattern.compile("^.*?\\s(?:class|@interface|enum)\\s*([^\\s]+)", Pattern.DOTALL);

    public static List<String> check(List<String> sources) throws Exception {
        List<StringSourceCode> stringSourceCodes = new ArrayList<>();
        for(String source : sources) {
            String packageName = "";
            Matcher packageNameMatcher = re_packageName.matcher(source);
            if(packageNameMatcher.find()) {
                packageName = packageNameMatcher.group(1) + ".";
            }

            Matcher classNameMatcher = re_className.matcher(source);
            if (classNameMatcher.find()) {
                stringSourceCodes.add(new StringSourceCode(packageName + classNameMatcher.group(1), source));
            }
        }

        StandardJavaFileManager fileManager = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = javac.getTask(null, new InMemoryFileManager(fileManager), diagnostics, null, null, stringSourceCodes);
        task.call();

        List<String> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            errors.add(diagnostic.getLineNumber() + ":" + diagnostic.getPosition() + " : " + diagnostic.getMessage(null) + " : " + diagnostic.getSource() );
        }

        return errors;
    }

    private static class StringSourceCode extends SimpleJavaFileObject {
        private String contents = null;

        public StringSourceCode(String className, String contents) throws Exception {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.contents = contents;
        }

        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return contents;
        }
    }

    private static class InMemoryFileManager implements JavaFileManager {
        private final StandardJavaFileManager fileManager;
        private final Map<String, ByteArrayOutputStream> buffers = new LinkedHashMap<>();

        InMemoryFileManager(StandardJavaFileManager fileManager) {
            this.fileManager = fileManager;
        }

        public ClassLoader getClassLoader(Location location) {
            return fileManager.getClassLoader(location);
        }

        public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
            return fileManager.list(location, packageName, kinds, recurse);
        }

        public String inferBinaryName(Location location, JavaFileObject file) {
            return fileManager.inferBinaryName(location, file);
        }

        public boolean isSameFile(FileObject a, FileObject b) {
            return fileManager.isSameFile(a, b);
        }

        public boolean handleOption(String current, Iterator<String> remaining) {
            return fileManager.handleOption(current, remaining);
        }

        public boolean hasLocation(Location location) {
            return fileManager.hasLocation(location);
        }

        public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
            if (location == StandardLocation.CLASS_OUTPUT && buffers.containsKey(className) && kind == JavaFileObject.Kind.CLASS) {
                final byte[] bytes = buffers.get(className).toByteArray();
                return new SimpleJavaFileObject(URI.create(className), kind) {
                    public InputStream openInputStream() {
                        return new ByteArrayInputStream(bytes);
                    }
                };
            }
            return fileManager.getJavaFileForInput(location, className, kind);
        }

        public JavaFileObject getJavaFileForOutput(Location location, final String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            return new SimpleJavaFileObject(URI.create(className), kind) {

                public OutputStream openOutputStream() {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    buffers.put(className, baos);
                    return baos;
                }
            };
        }

        public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
            return fileManager.getFileForInput(location, packageName, relativeName);
        }

        public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
            return fileManager.getFileForOutput(location, packageName, relativeName, sibling);
        }

        public void flush() {
            // Do nothing
        }

        public void close() throws IOException {
            fileManager.close();
        }

        public int isSupportedOption(String option) {
            return fileManager.isSupportedOption(option);
        }

        public String inferModuleName(Location location) throws IOException {
            return fileManager.inferModuleName(location);
        }

        public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException {
            return fileManager.listLocationsForModules(location);
        }
    }
}
