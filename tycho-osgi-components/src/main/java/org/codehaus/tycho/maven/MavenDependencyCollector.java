package org.codehaus.tycho.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.FeatureDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.model.PluginRef;
import org.codehaus.tycho.osgitools.BundleReader;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Constants;
import org.sonatype.tycho.ArtifactDescriptor;
import org.sonatype.tycho.ArtifactKey;
import org.sonatype.tycho.ReactorProject;

/**
 * Generates list of Maven dependencies from project OSGi/Eclipse dependencies
 */
public class MavenDependencyCollector
    extends ArtifactDependencyVisitor
{
    public static final String P2_CLASSIFIER_BUNDLE = "osgi.bundle";
    public static final String P2_CLASSIFIER_FEATURE = "org.eclipse.update.feature";
    private static final List<String> DOT_CLASSPATH = Collections.singletonList( "." );
    private static final List<Dependency> NO_DEPENDENCIES = Collections.emptyList();

    private final MavenProject project;

    private final Logger logger;

    private BundleReader bundleReader;

    public MavenDependencyCollector( MavenProject project, BundleReader bundleReader, Logger logger )
    {
        this.project = project;
        this.logger = logger;
        this.bundleReader = bundleReader;
    }

    @Override
    public boolean visitFeature( FeatureDescription feature )
    {
        addDependency( feature );
        return true; // keep visiting
    }

    @Override
    public void visitPlugin( PluginDescription plugin )
    {
        addDependency( plugin );
    }

    @Override
    public void missingPlugin( PluginRef ref, List<ArtifactDescriptor> walkback )
    {
        // we don't handle multi-environment target platforms well, so
        // missing environment specific bundles should not fail the build

        if ( ref.getOs() == null && ref.getWs() == null && ref.getArch() == null )
        {
            super.missingPlugin( ref, walkback );
        }
        else
        {
            logger.warn( "Missing environment specific bundle " + ref.toString() );
        }
    }

    protected void addDependency( ArtifactDescriptor artifact )
    {
        List<Dependency> dependencyList = new ArrayList<Dependency>();
        if ( artifact.getMavenProject() != null )
        {
            if ( !artifact.getMavenProject().sameProject( project ) )
            {
                dependencyList.addAll( newProjectDependencies( artifact ) );
            }
        }
        else
        {
            dependencyList.addAll( newExternalDependencies( artifact ) );
        }
        Model model = project.getModel();
        for ( Dependency dependency : dependencyList )
        {
            model.addDependency( dependency );
        }
    }

    protected List<Dependency> newExternalDependencies( ArtifactDescriptor artifact )
    {
        File location = artifact.getLocation();
        if ( !location.isFile() || !location.canRead() )
        {
            logger.warn( "Dependency at location " + location
                + " can not be represented in Maven model and will not be visible to non-OSGi aware Maven plugins" );
            return NO_DEPENDENCIES;
        }
        List<Dependency> result = new ArrayList<Dependency>();
        if ( ArtifactKey.TYPE_ECLIPSE_PLUGIN.equals( artifact.getKey().getType() ) )
        {
            for ( String classpathElement : getClasspathElements( location ) )
            {
                if ( ".".equals( classpathElement ) )
                {
                    result.add( createSystemScopeDependency( artifact.getKey(), location ) );
                }
                else
                {
                    File nestedJarOrDir = bundleReader.getEntry( location, classpathElement );
                    if ( nestedJarOrDir != null )
                    {
                        if ( nestedJarOrDir.isFile() )
                        {
                            Dependency nestedJarDependency =
                                createSystemScopeDependency( artifact.getKey(), nestedJarOrDir );
                            nestedJarDependency.setClassifier( classpathElement );
                            result.add( nestedJarDependency );
                        }
                        else if ( nestedJarOrDir.isDirectory() )
                        {
                            // system-scoped dependencies on directories are not supported
                            logger.warn( "Dependency from "
                                + project.getBasedir()
                                + " to nested directory classpath entry "
                                + nestedJarOrDir
                                + " can not be represented in Maven model and will not be visible to non-OSGi aware Maven plugins" );
                        }
                    }
                }
            }
        }
        else
        {
            result.add( createSystemScopeDependency( artifact.getKey(), location ) );
        }
        return result;
    }

    private List<String> getClasspathElements( File bundleLocation )
    {
        ManifestElement[] classpathHeader =
            bundleReader.parseHeader( Constants.BUNDLE_CLASSPATH, bundleReader.loadManifest( bundleLocation ) );
        if ( classpathHeader == null || classpathHeader.length == 0 )
        {
            return DOT_CLASSPATH;
        }
        List<String> result = new ArrayList<String>( classpathHeader.length );
        for ( ManifestElement classPathElement : classpathHeader )
        {
            result.add( classPathElement.getValue() );
        }
        return result;
    }

    private Dependency createSystemScopeDependency( ArtifactKey artifactKey, File location )
    {
        /* see RepositoryLayoutHelper#getP2Gav */
        return createSystemScopeDependency( artifactKey, "p2." + artifactKey.getType() , location );
    }
    
    private Dependency createSystemScopeDependency( ArtifactKey artifactKey, String groupId, File location )
    {
        Dependency dependency = new Dependency();
        dependency.setGroupId( groupId ); 
        dependency.setArtifactId( artifactKey.getId() );
        dependency.setVersion( artifactKey.getVersion() );
        dependency.setScope( Artifact.SCOPE_SYSTEM );
        dependency.setSystemPath( location.getAbsolutePath() );
        return dependency;
    }

    protected List<Dependency> newProjectDependencies( ArtifactDescriptor artifact )
    {
        ReactorProject dependentMavenProjectProxy = artifact.getMavenProject();
        List<Dependency> result = new ArrayList<Dependency>();
        if ( ArtifactKey.TYPE_ECLIPSE_PLUGIN.equals( dependentMavenProjectProxy.getPackaging() ) )
        {
            for ( String classpathElement : getClasspathElements( dependentMavenProjectProxy.getBasedir() ) )
            {
                if ( ".".equals( classpathElement ) )
                {
                    result.add( createProvidedScopeDependency( dependentMavenProjectProxy ) );
                }
                else /* nested classpath entry */
                {
                    File jar = new File( dependentMavenProjectProxy.getBasedir(), classpathElement );
                    // we can only add a system scope dependency for an existing (checked-in) jar file
                    // otherwise maven will throw a DependencyResolutionException
                    if ( jar.isFile() )
                    {
                        Dependency systemScopeDependency =
                            createSystemScopeDependency( artifact.getKey(), artifact.getMavenProject().getGroupId(),
                                                         jar );
                        systemScopeDependency.setClassifier( classpathElement );
                        result.add( systemScopeDependency );
                    }
                    else
                    {
                        logger.warn( "Dependency from "
                            + project.getBasedir()
                            + " to nested classpath entry "
                            + jar.getAbsolutePath()
                            + " can not be represented in Maven model and will not be visible to non-OSGi aware Maven plugins" );
                    }
                }
            }
        } else {
            result.add( createProvidedScopeDependency( dependentMavenProjectProxy ) );
        }
        return result;
    }

    private Dependency createProvidedScopeDependency( ReactorProject dependentReactorProject )
    {
        Dependency dependency = new Dependency();
        dependency.setArtifactId( dependentReactorProject.getArtifactId() );
        dependency.setGroupId( dependentReactorProject.getGroupId() );
        dependency.setVersion( dependentReactorProject.getVersion() );
        dependency.setType( dependentReactorProject.getPackaging() );
        dependency.setScope( Artifact.SCOPE_PROVIDED );
        return dependency;
    }
}
