package tpietzsch.shadergen.generate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.stringtemplate.v4.ST;

public class StringTemplateUtils
{
	public static ST loadAndPatchSnippet(
			final Class< ? > resourceContext,
			final String resourceName,
			final List< String > keys )
			throws IOException
	{
		final InputStream stream = resourceContext.getResourceAsStream( resourceName );
		final BufferedReader reader = new BufferedReader( new InputStreamReader( stream ) );
		final StringBuilder builder = new StringBuilder();
		String line;
		while ( ( line = reader.readLine() ) != null )
		{
			builder.append( line );
			builder.append( "\n" );
		}
		final String snippet = builder.toString();

		final ArrayList< String > searchList = new ArrayList<>();
		final ArrayList< String > replacementList = new ArrayList<>();
		for ( final String key : keys )
		{
			searchList.add( key );
			replacementList.add( "$" + key + "$" );
		}

		final String patched = StringUtils.replaceEach(
				snippet,
				searchList.toArray( new String[ 0 ] ),
				replacementList.toArray( new String[ 0 ] ) );

		return new ST( patched, '$', '$' );
	}

	public static void clearAttributes( final ST st )
	{
		if ( st.getAttributes() != null )
			new ArrayList<>( st.getAttributes().keySet() ).forEach( st::remove );
	}
}
