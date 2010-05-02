package org.codehaus.tycho.model;

import de.pdark.decentxml.Element;

/**
 * This object represents information of a bundle. This class is a copy of the BundleInfo class in
 * org.eclipse.equinox.simpleconfigurator
 */
public class BundleConfiguration
{
    public static int NO_STARTLEVEL = -1;

    Element configuration = null;

    public BundleConfiguration( Element pluginDom )
    {
        configuration = pluginDom;
    }

    public BundleConfiguration( String id, int startLevel, boolean autoStart )
    {
        this.configuration = new Element( "plugin" );

        setAutoStart( autoStart );
    }

    public boolean isAutoStart()
    {
        return Boolean.parseBoolean( configuration.getAttributeValue( "autoStart" ) );
    }

    public void setAutoStart( boolean autoStart )
    {
        configuration.setAttribute( "autoStart", Boolean.toString( autoStart ) );
    }

    public String getId()
    {
        return configuration.getAttributeValue( "id" );
    }

    public void setId( String id )
    {
        configuration.setAttribute( "id", id );
    }

    public int getStartLevel()
    {
        String sl = configuration.getAttributeValue( "startLevel" );
        if ( sl != null )
        {
            return Integer.decode( sl ).intValue();
        }
        return -1;
    }

    public void setStartLevel( int startLevel )
    {
        configuration.setAttribute( "startLevel", startLevel != NO_STARTLEVEL ? Integer.toString( startLevel ) : null );
    }
}
