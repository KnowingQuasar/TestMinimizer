import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.ListUtils;
import org.apache.maven.cli.MavenCli;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TestMinimizer {
    private final MavenCli mavenCli;
    private final String pathToProject;
    private final String pathToTestClass;
    private final String testClassName;
    private final String testMethodName;
    private CompilationUnit testClassCu;

    public TestMinimizer(String pathToProject, String pathToTestClass, String testClassName, String testMethodName) {
        mavenCli = new MavenCli();
        this.pathToTestClass = pathToTestClass;
        this.pathToProject = pathToProject;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        System.setProperty("maven.multiModuleProjectDirectory", pathToProject);
    }

    private CompilationUnit createCompilationUnit(String fullClassPath) {
        FileInputStream in;
        CompilationUnit cu = null;

        try {
            in = new FileInputStream(fullClassPath);
        } catch (FileNotFoundException fex) {
            fex.printStackTrace();
            return null;
        }

        try {
            // parse the file
            cu = StaticJavaParser.parse(in);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return cu;
    }

    private boolean isCompiled(String cliOutput) {
        return !cliOutput.contains("COMPILATION ERROR");
    }

    private boolean isTestPassed(String cliOutput) {
        return !cliOutput.contains("There are test failures.");
    }

    private boolean isTestFailing(String testClass) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);

        mavenCli.doMain(new String[]{"-Dtest=" + testClass + "#" + testMethodName, "test"}, pathToProject,
                System.out, printStream);
        String cliOutput = byteArrayOutputStream.toString();
        if (isCompiled(cliOutput)) {
            return !isTestPassed(cliOutput);
        } else {
            return false;
        }
    }

    private BlockStmt findMethodBody(CompilationUnit cu) throws Exception {
        if (cu == null) {
            throw new Exception("Could not parse the java class into compilation unit!");
        }
        ClassOrInterfaceDeclaration mainClass = cu.getClassByName(testClassName).orElse(null);

        if (mainClass == null) {
            throw new Exception("Could not find class with name: " + testClassName + " in file!");
        }

        List<MethodDeclaration> methods = mainClass.getMethods();

        if (methods == null || methods.size() == 0) {
            throw new Exception("Could not find any methods in the class with name: " + testClassName);
        }

        MethodDeclaration chosenMethod = null;
        for (MethodDeclaration method : methods) {
            if (method.getName().asString().equals(testMethodName)) {
                chosenMethod = method;
                break;
            }
        }

        if (chosenMethod == null) {
            throw new Exception("Could not find method with name: " + testMethodName + " in class: " + testClassName);
        }

        BlockStmt blockStmt = chosenMethod.getBody().orElse(null);

        if (blockStmt == null) {
            throw new Exception("Could not get body of method with name: " + testMethodName + " in class: " + testClassName);
        }

        return blockStmt;
    }

    private List<List<Statement>> createPartitions(List<Statement> partition, int n) {
        return ListUtils.partition(partition, (int) Math.round((double) partition.size() / (double) n));
    }

    private boolean checkTestForFailure(List<Statement> partition) throws Exception {
        CompilationUnit temp = testClassCu.clone();
        BlockStmt blockStmt = findMethodBody(temp);
        blockStmt.setStatements(NodeList.nodeList(partition));

        ClassOrInterfaceDeclaration mainClass = temp.getClassByName(testClassName).orElseThrow();
        mainClass.setName(testClassName + "Generated");

        String tmpFileName = pathToTestClass.replace(".java", "Generated.java");
        FileWriter fileWriter = new FileWriter(tmpFileName);
        fileWriter.write(temp.toString());
        fileWriter.close();
        boolean testResult = isTestFailing(testClassName + "Generated");

        File file = new File(tmpFileName);
        file.delete();

        return testResult;
    }

    private List<Statement> processPartitions(List<Statement> statements, int n) throws Exception {
        List<List<Statement>> partitions = createPartitions(statements, n);
        List<List<Statement>> complements = new ArrayList<>();

        for (List<Statement> partition : partitions) {
            if (checkTestForFailure(partition)) {
                if (partition.size() > 1) {
                    return processPartitions(partition, 2);
                } else {
                    return partition;
                }
            } else {
                List<Statement> complement = new NodeList<>();
                for (Statement statement : statements) {
                    if (!partition.contains(statement)) {
                        complement.add(statement);
                    }
                }
                complements.add(complement);
            }
        }
        if (n != 2) {
            for (List<Statement> complement : complements) {
                if (checkTestForFailure(complement)) {
                    return processPartitions(complement, Math.max(n - 1, 2));
                }
            }
        }

        if (n >= statements.size()) {
            return statements;
        }

        return processPartitions(statements, 2 * n);
    }

    public String minimizeTest() throws Exception {
        testClassCu = createCompilationUnit(pathToTestClass);
        BlockStmt blockStmt = findMethodBody(testClassCu);

        NodeList<Statement> allStatements = blockStmt.getStatements();

        List<Statement> minimalStatements = processPartitions(allStatements, 2);

        blockStmt.setStatements(NodeList.nodeList(minimalStatements));

        return testClassCu.toString();
    }
}
