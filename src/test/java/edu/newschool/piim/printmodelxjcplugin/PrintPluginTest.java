package edu.newschool.piim.printmodelxjcplugin;

import org.jvnet.jaxb2.maven2.AbstractXJC2Mojo;
import org.jvnet.jaxb2.maven2.test.RunXJC2Mojo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PrintPluginTest extends RunXJC2Mojo {

    //GeneratedCodeFixture generatedCodeFixture = new GeneratedCodeFixture("src/test/resources/expected/{0}.java.txt",
     //       "target/generated-sources/xjc/org/example/visitor/{0}.java");

    @Override
    public File getSchemaDirectory() {
        return new File(getBaseDir(), "src/test/resources");
    }

    @Override
    protected void configureMojo(AbstractXJC2Mojo mojo) {
        super.configureMojo(mojo);
        mojo.setForceRegenerate(true);
        mojo.setWriteCode(true);
        
    }

    @Override
    public List<String> getArgs() {
        final List<String> args = new ArrayList<String>(super.getArgs());
        args.add("-MyPluginClass");
       // args.add("-quite");
       // args.add("-MarinePlugin-package:edu.example.marineplugin");
        System.out.println(args.toString());
        return args;
    }

    @Override
    public void testExecute() throws Exception {

        super.testExecute();

        //generatedCodeFixture.assertAllFiles();
    }
}
