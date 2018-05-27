/*
 * Copyright (c) 2015. Troels Liebe Bentsen <tlb@nversion.dk>
 * Copyright (c) 2016. Nordea Bank AB
 * Licensed under the MIT license (LICENSE.txt)
 */

package com.nordea.oss.copybook.codegen;

import com.nordea.oss.ByteUtils;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import javax.script.ScriptException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class CopyBookConverterTest {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();


    @org.junit.Test
    public void testBasicConversionAndVerifyWithCompilation() throws Exception {
        CopyBookConverter converter = new CopyBookConverter();
        List<String> convertedJavaSources = new ArrayList<>();

        InputStream hospitalSample = this.getClass().getResourceAsStream("../../../../../hospital_test.txt");
        convertedJavaSources.addAll(converter.convert(hospitalSample, "mypackage", "MyHospital", "none", "UTF-8", "nested", null));

        InputStream rootObjSample = this.getClass().getResourceAsStream("../../../../../rootobj.txt");
        convertedJavaSources.addAll(converter.convert(rootObjSample, "mypackage", "RootObj", "none", "UTF-8", "nested", null));

        List<String> errors = CompileGeneratedSource(convertedJavaSources);
        assertEquals(0, errors.size());
    }

    @org.junit.Test
    public void testBasicConversionWithError() throws Exception {
        expectedEx.expect(ScriptException.class);
        expectedEx.expectMessage("Could not parse line");
        String sample = "05 ERROR PIC 9(5) OCCURSZ 6.";
        CopyBookConverter converter = new CopyBookConverter();
        converter.convert(sample, "mypackage", "MyHospital", "none", "UTF-8", "nested", null);
    }

    @org.junit.Test
    public void testToleratePicWithSignLeadingSeparate() throws Exception {
        String sample = "05 ERROR PIC 9(5) SIGN LEADING SEPARATE.";
        CopyBookConverter converter = new CopyBookConverter();
        converter.convert(sample, "mypackage", "MyHospital", "none", "UTF-8", "nested", null);
    }

    @org.junit.Test
    public void testTolerateEnum() throws Exception {
        String sample = "88 ENUM-Foo VALUE 'bar'.";
        CopyBookConverter converter = new CopyBookConverter();
        converter.convert(sample, "mypackage", "MyHospital", "none", "UTF-8", "nested", null);
    }

    @org.junit.Test
    public void testMultiFileConversion() throws Exception {
        File basePath = new File(this.getClass().getResource("../../../../").toURI());
        CopyBookConverter converter = new CopyBookConverter();
        converter.convertFiles(basePath.getAbsolutePath(), Pattern.compile("\\.txt$"), basePath.getAbsolutePath(), "mypackage", "none", "UTF-8", "nested");
    }

    private List<String> CompileGeneratedSource(List<String> convertedJavaSources) throws Exception {
        File annotationsFolder = new File(this.getClass().getResource(".").getFile() + "../../../../../../../../copybook4java/src/main/java/com/nordea/oss/copybook/annotations");

        List<String> sources = new ArrayList<>();
        for(File file : annotationsFolder.listFiles()) {
            if(file.isFile() && file.getName().endsWith(".java")) {
                sources.add(new String(ByteUtils.toByteArray(new FileInputStream(file)), StandardCharsets.UTF_8));
            }
        }
        sources.addAll(convertedJavaSources);
        return JavaSyntaxChecker.check(sources);
    }

}