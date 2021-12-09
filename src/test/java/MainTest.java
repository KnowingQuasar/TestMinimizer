import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;

public class MainTest {
    @Test
    public void minimizeTest() {
        TestMinimizer testMinimizer = new TestMinimizer("C:\\Users\\irodr\\Documents\\School\\2021-2022\\CS 6830\\commons-validator",
                "C:\\Users\\irodr\\Documents\\School\\2021-2022\\CS 6830\\commons-validator\\src\\test\\java\\org\\apache\\commons\\validator\\routines\\UrlValidatorTest.java",
                "UrlValidatorTest", "testValidator248");
//        TestMinimizer testMinimizer = new TestMinimizer("C:\\Users\\irodr\\Documents\\School\\2021-2022\\CS 6830\\MavenAppUnderTest",
//                "C:\\Users\\irodr\\Documents\\School\\2021-2022\\CS 6830\\MavenAppUnderTest\\src\\test\\java\\com\\maven\\app\\MainTest.java",
//                "MainTest", "test3");
        try {
            String results = testMinimizer.minimizeTest();
            FileWriter fileWriter = new FileWriter("MinimizedTest.java");
            fileWriter.write(results);
            fileWriter.close();
            Assertions.assertTrue(true);
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail();
        }
    }
}
