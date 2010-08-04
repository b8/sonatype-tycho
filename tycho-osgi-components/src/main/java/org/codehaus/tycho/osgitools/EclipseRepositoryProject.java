package org.codehaus.tycho.osgitools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.ArtifactDependencyWalker;
import org.codehaus.tycho.ArtifactKey;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.model.ProductConfiguration;

/**
 * An eclipse repository project produces a p2 repository where a set of products are published.
 */
@Component( role = TychoProject.class, hint = TychoProject.ECLIPSE_REPOSITORY )
public class EclipseRepositoryProject
    extends AbstractArtifactBasedProject
{

    /**
     * The published repository is always under the id of the maven project: this published
     * repository can contain multiple products.
     */
    public ArtifactKey getArtifactKey( MavenProject project )
    {
        String id = project.getArtifactId();
        String version = getOsgiVersion( project );

        return new ArtifactKey( TychoProject.ECLIPSE_REPOSITORY, id, version );
    }

    @Override
    protected ArtifactDependencyWalker newDependencyWalker( MavenProject project, TargetEnvironment environment )
    {
        final List<ProductConfiguration> products = loadProducts( project );
        return new AbstractArtifactDependencyWalker( getTargetPlatform( project, environment ),
                                                     getEnvironments( project, environment ) )
        {
            public void walk( ArtifactDependencyVisitor visitor )
            {
                for ( ProductConfiguration product : products )
                {
                    traverseProduct( product, visitor );
                }
            }
        };
    }

    /**
     * Parses the product configuration files
     *
     * @param project
     * @return
     */
    protected List<ProductConfiguration> loadProducts( final MavenProject project )
    {
        List<ProductConfiguration> products = new ArrayList<ProductConfiguration>();
        for ( File file : getProductFiles( project ) )
        {
            try
            {
                products.add( ProductConfiguration.read( file ) );
            }
            catch ( IOException e )
            {
                throw new RuntimeException( "Could not read product configuration file " + file.getAbsolutePath(), e );
            }
        }
        return products;
    }

    /**
     * Looks for all files at the base of the project that extension is ".product" Duplicated in the
     * P2GeneratorImpl
     *
     * @param project
     * @return The list of product files to parse for an eclipse-repository project
     */
    public List<File> getProductFiles( MavenProject project )
    {
        File projectLocation = project.getBasedir();
        List<File> res = new ArrayList<File>();
        for ( File f : projectLocation.listFiles() )
        {
            if ( f.isFile() && f.getName().endsWith( ".product" ) )
            {
                res.add( f );
            }
        }
        return res;
    }
}