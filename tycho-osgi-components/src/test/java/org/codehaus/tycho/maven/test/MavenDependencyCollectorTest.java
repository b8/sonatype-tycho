package org.codehaus.tycho.maven.test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.tycho.testing.AbstractTychoMojoTestCase;
import org.junit.Assert;

public class MavenDependencyCollectorTest
    extends AbstractTychoMojoTestCase
{

    public void testNestedJars()
        throws Exception
    {
        File targetPlatform = getBasedir( "targetplatforms/nestedJar" );
        List<MavenProject> projects = getSortedProjects( getBasedir( "projects/mavendeps" ), targetPlatform );
        {
            // 1. project with dependency to external bundle with nested jar
            MavenProject project = projects.get( 1 );
            final String plainJarPath = "target/targetplatforms/nestedJar/plugins/nested_1.0.0.jar";
            final String nestedJarPath = "target/local-repo/.cache/tycho/nested_1.0.0.jar/lib/lib.jar";
            List<Dependency> mavenDependencies = project.getModel().getDependencies();
            Assert.assertEquals( 2, mavenDependencies.size() );
            final String expectedGroupId = "p2.eclipse-plugin";
            final String expectedArtifactId = "nested";
            final String expectedVersion = "1.0.0";
            final String expectedType = "jar";
            final String expectedScope = Artifact.SCOPE_SYSTEM;
            // assert that dependencies to both plain jar and nested jar are injected back into maven model
            assertDependenciesContains( mavenDependencies, expectedGroupId, expectedArtifactId, expectedVersion, null,
                                        expectedType, expectedScope, new File( getBasedir(), plainJarPath ) );
            assertDependenciesContains( mavenDependencies, expectedGroupId, expectedArtifactId, expectedVersion,
                                        "lib/lib.jar", expectedType, expectedScope, new File( getBasedir(),
                                                                                              nestedJarPath ) );
        }
        {
            // 2. project with dependency to bundle with nested jar within the same reactor
            MavenProject project = projects.get( 3 );
            List<Dependency> mavenDependencies = project.getModel().getDependencies();
            // assert that dependencies to both reactor module and checked-in nested jar are injected back into maven
            // model.
            // Also, dependency to missing nested jar must *not* be injected (would throw
            // MavenDependencyResolutionException otherwise)
            Assert.assertEquals( 2, mavenDependencies.size() );
            final String expectedGroupId = "mavenDependencies";
            final String expectedArtifactId = "p002";
            final String expectedVersion = "1.0.0";
            assertDependenciesContains( mavenDependencies, expectedGroupId, expectedArtifactId, expectedVersion, null,
                                        "eclipse-plugin", Artifact.SCOPE_PROVIDED, null );
            assertDependenciesContains( mavenDependencies, expectedGroupId, expectedArtifactId, expectedVersion,
                                        "lib/lib.jar", "jar", Artifact.SCOPE_SYSTEM,
                                        new File( getBasedir( "projects/mavendeps" ), "p002/lib/lib.jar" ) );
        }
    }

    private void assertDependenciesContains( List<Dependency> mavenDependencies, String groupId, String artifactId,
                                             String version, String classifier, String type, String scope,
                                             File systemLocation )
        throws IOException
    {
        for ( Dependency dependency : mavenDependencies )
        {
            boolean systemLocationEquals = true;
            if ( systemLocation != null )
            {
                systemLocationEquals =
                    dependency.getSystemPath() != null
                        && systemLocation.getCanonicalFile().getAbsolutePath().equals( new File(
                                                                                                 dependency.getSystemPath() ).getCanonicalFile().getAbsolutePath() );
            }
            if ( systemLocationEquals //
                && groupId.equals( dependency.getGroupId() ) //
                && artifactId.equals( dependency.getArtifactId() )//
                && version.equals( dependency.getVersion() ) //
                && type.equals( dependency.getType() )//
                && scope.equals( dependency.getScope() ) //
            )
            {
                if ( classifier == null )
                {
                    if ( dependency.getClassifier() == null )
                    {
                        return;
                    }
                }
                else
                {
                    if ( classifier.equals( dependency.getClassifier() ) )
                    {
                        return;
                    }
                }
            }
        }
        fail( "Expected dependency [" + groupId + ":" + artifactId + ":" + version + ":" + classifier + ":" + type
            + ":" + scope + ":" + systemLocation + "] not found in actual dependencies: "
            + toDebugString( mavenDependencies ) );
    }

    private static String toDebugString( List<Dependency> mavenDependencies )
    {
        StringBuilder sb = new StringBuilder();
        for ( Dependency dependency : mavenDependencies )
        {
            sb.append( toDebugString( dependency ) );
        }
        return sb.toString();
    }

    private static String toDebugString( Dependency dependency )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( '[' );
        sb.append( dependency.getGroupId() );
        sb.append( ':' );
        sb.append( dependency.getArtifactId() );
        sb.append( ':' );
        sb.append( dependency.getVersion() );
        sb.append( ':' );
        sb.append( dependency.getClassifier() );
        sb.append( ':' );
        sb.append( dependency.getType() );
        sb.append( ", scope: " );
        sb.append( dependency.getScope() );
        sb.append( ", systemPath: " );
        sb.append( dependency.getSystemPath() );
        sb.append( ']' );
        return sb.toString();
    }

}
