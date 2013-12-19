package codegenerator;

import fitnesse.slim.instructions.CallAndAssignInstruction;
import fitnesse.slim.instructions.CallInstruction;
import fitnesse.slim.instructions.Instruction;
import fitnesse.testsystems.slim.HtmlTableScanner;
import fitnesse.testsystems.slim.SlimTestContextImpl;
import fitnesse.testsystems.slim.Table;
import fitnesse.testsystems.slim.TableScanner;
import fitnesse.testsystems.slim.tables.ScriptTable;
import fitnesse.testsystems.slim.tables.SlimAssertion;
import fitnesse.testsystems.slim.tables.SlimTable;
import fitnesse.testsystems.slim.tables.SyntaxError;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPageUtil;
import fitnesse.wiki.mem.InMemoryPage;
import util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class TableParser {
    public static String separator = "\t";

    public static Iterator<Table> makeTables(String tableText) {
        WikiPage root = InMemoryPage.makeRoot("root");
        WikiPageUtil.setPageContents(root, tableText);
        TableScanner ts = new HtmlTableScanner(root.getData().getHtml());
        return ts.iterator();
    }

    public static List<SlimAssertion> listAssertionsFromScriptTables(Iterator<Table> tables) {
        List<SlimAssertion> assertions = new ArrayList<SlimAssertion>();
        while(tables.hasNext()) {
            ScriptTable script = new ScriptTable(tables.next(), "id", new SlimTestContextImpl());
            try {
                assertions.addAll(script.getAssertions());
            } catch (SyntaxError syntaxError) {
                syntaxError.printStackTrace();
            }
        }
        return assertions;
    }

    public static String assertionsString(List<SlimAssertion> assertions) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < assertions.size(); i++) {
            SlimAssertion slimAssertion = assertions.get(i);
            Instruction instruction = slimAssertion.getBaseInstruction();

            if (instruction instanceof CallInstruction) {
                CallInstruction call = (CallInstruction) instruction;
                sb.append("method=" + call.getMethodName() + separator + "args=" + Arrays.asList(call.getArgs()));
                if(slimAssertion.getExpectation() instanceof SlimTable.ReturnedValueExpectation) {
                    sb.append(" expectation=" + ((SlimTable.ReturnedValueExpectation)slimAssertion.getExpectation()).getOriginalContent());
                }
                sb.append("\n");
            } else if (instruction instanceof CallAndAssignInstruction) {
                CallAndAssignInstruction callAndAssign = (CallAndAssignInstruction) instruction;
                sb.append("variable=" + callAndAssign.getSymbolName() + separator +
                          "method=" + callAndAssign.getMethodName() + separator +
                          "args=" + Arrays.asList(callAndAssign.getArgs()) +
                          "\n");
            }
        }
        return sb.toString();
    }

    public static List<File> gatherFiles(String dir, List<File> files) {
        File[] ls = FileUtil.getDirectoryListing(new File(dir));
        for(File f: ls) {
            if(f.isDirectory()) {
                gatherFiles(f.getPath(), files);
            } else if(f.isFile() && f.getName().endsWith("content.txt")) {
                files.add(f);
            }
        }
        return files;
    }

    /**
     * Given a folder containing fitnesse tests, it recursively finds them and outputs an easy
     * to parse text representation that can be used for code generation.
     * This is being developed for porting the accounting suites to the banana stand project.
     *
     * It also writes a summary containing filename, source and text representation to output.txt
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if(args.length == 0) {
            System.out.println("Please provide the folder containing the fitnesse tests you want to process");
            return;
        }
        String dirName = args[0];
        List<File> files = gatherFiles(dirName, new ArrayList<File>());

        StringBuffer output = new StringBuffer();

        for(File f: files) {
            output.append("File: " + f);
            output.append("\n");
            String content = FileUtil.getFileContent(f);

            Iterator<Table> tables = makeTables(content);
            List<SlimAssertion> assertions = listAssertionsFromScriptTables(tables);
            String assertionsString = assertionsString(assertions);

            String normalizedPath = f.getPath().replace(dirName, "").replace(File.separator, "_");

            if(assertionsString.length() > 0) {
                FileUtil.createFile(normalizedPath, assertionsString);
            }
            output.append(content);
        }

        FileUtil.createFile("output.txt", output.toString());
        System.out.println("Finished converting " + files.size() + " file(s)");
    }
}
