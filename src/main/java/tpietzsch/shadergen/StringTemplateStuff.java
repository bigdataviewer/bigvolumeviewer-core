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
	static class LoadTemplate
	{
		private final Class< ? > resourceContext;

		private final String resourceName;

		private final List< String > variables;

		private ST st;

		public LoadTemplate(
				final Class< ? > resourceContext,
				final String resourceName,
				final List< String > variables )
		{
			this.resourceContext = resourceContext;
			this.resourceName = resourceName;
			this.variables = variables;
		}

		public void run() throws IOException
		{
			final String template = readSnippet();
			System.out.println( "template = " + template );
			st = makeTemplate( template );
			st.add( "convert", "CONVERT" );
			st.add( "intensity_offset", "OFF" );
			st.add( "intensity_scale", "SCALE" );

			System.out.println();
			System.out.println();
			System.out.println();
			System.out.println(st.render());
		}

		private String readSnippet() throws IOException
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

			ArrayList< String > searchList = new ArrayList<>();
			ArrayList< String > replacementList = new ArrayList<>();
			searchList.add( "$" );
			replacementList.add( "\\$" );
			for ( String variable : variables )
			{
				searchList.add( variable );
				replacementList.add( "$" + variable + "$" );
			}

			return StringUtils.replaceEach( builder.toString(),
					searchList.toArray( new String[ 0 ] ),
					replacementList.toArray( new String[ 0 ] ) );
		}

		private ST makeTemplate( String template )
		{
			return new ST( template, '$', '$' );
		}
	}

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
		searchList.add( "$" );
		replacementList.add( "\\$" );
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

	public static void main( String[] args ) throws IOException
	{
		final LoadTemplate t = new LoadTemplate( StringTemplateStuff.class, "convertlin.fp", Arrays.asList( "convert", "intensity_offset", "intensity_scale" ) );
		t.run();
	}
}
