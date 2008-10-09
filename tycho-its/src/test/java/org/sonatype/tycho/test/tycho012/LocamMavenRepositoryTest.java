package org.sonatype.tycho.test.tycho012;

import org.apache.maven.it.Verifier;
import org.sonatype.tycho.test.AbstractTychoIntegrationTest;

public class LocamMavenRepositoryTest extends AbstractTychoIntegrationTest {

	public void testLocalMavenRepository() throws Exception {
        Verifier v01 = getVerifier("tycho012/build01/bundle01");
        v01.executeGoal("install");
        v01.verifyErrorFreeLog();

        Verifier v02 = getVerifier("tycho012/build02/bundle02");
        v02.executeGoal("install");
        v02.verifyErrorFreeLog();
	}
}