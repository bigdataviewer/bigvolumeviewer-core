package tpietzsch.shadergen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.stringtemplate.v4.ST;

public class StringTemplateStuff
{
	public static ST loadAndPatchSnippet(
			final Class< ? > resourceContext,
			final String resourceName,
			final List< String > keys )
			throws IOException
	{
		final InputStream stream = resourceContext.getResourceAsStream( resourceName );
		final BufferedReader reader = new BufferedReader( new InputStreamReader( stream ) );
		StringBuilder builder = new StringBuilder();
		String line;
		while ( ( line = reader.readLine() ) != null )
		{
			builder.append( line );
			builder.append( "\n" );
		}
		final String snippet = builder.toString();

		ArrayList< String > searchList = new ArrayList<>();
		ArrayList< String > replacementList = new ArrayList<>();
		for ( String key : keys )
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

	public static void clearAttributes( ST st )
	{
		if ( st.getAttributes() != null )
			new ArrayList<>( st.getAttributes().keySet() ).forEach( st::remove );
	}
}
