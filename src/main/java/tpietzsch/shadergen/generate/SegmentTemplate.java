package tpietzsch.shadergen.generate;

import static tpietzsch.shadergen.generate.StringTemplateUtils.clearAttributes;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.stringtemplate.v4.ST;

public class SegmentTemplate
{
	private final ST st;

	private final List< String > keys;

	private static final AtomicInteger idGen = new AtomicInteger();

	public SegmentTemplate(
			final String resourceName,
			final String ... keys )
	{
		this( tryGetContext(), resourceName, Arrays.asList( keys ) );
	}

	public SegmentTemplate(
			final String resourceName,
			final List< String > keys )
	{
		this( tryGetContext(), resourceName, keys );
	}

	public SegmentTemplate(
			final Class< ? > resourceContext,
			final String resourceName,
			final List< String > keys )
	{
		try
		{
			st = StringTemplateUtils.loadAndPatchSnippet( resourceContext, resourceName, keys );
			this.keys = keys;
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}
	}

	public Segment instantiate()
	{
		return instantiate( proposeKeyToIdentifierMap() );
	}

	private Segment instantiate( final Map< String, String > keyToIdentifier )
	{
		return new Segment( this, keyToIdentifier );
	}

	private Map< String, String > proposeKeyToIdentifierMap()
	{
		final Map< String, String > keyToIdentifier = new HashMap<>();
		int baseId = idGen.getAndAdd( keys.size() );
		for ( final String key : keys )
		{
			final String instance = String.format( "%s__%d__", key, baseId++ );
			keyToIdentifier.put( key, instance );
		}
		return keyToIdentifier;
	}

	String render( final Map< String, String > keyToIdentifier )
	{
		clearAttributes( st );
		keys.forEach( key -> st.add( key, keyToIdentifier.get( key ) ) );
		return st.render();
	}

	private static Class<?> tryGetContext()
	{
		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		try
		{
			final Class< ? > klass = SegmentTemplate.class.getClassLoader().loadClass( stackTrace[ 3 ].getClassName() );
			System.out.println( "klass = " + klass );
			return klass;
		}
		catch ( final ClassNotFoundException e )
		{
			throw new RuntimeException( e );
		}
	}
}
