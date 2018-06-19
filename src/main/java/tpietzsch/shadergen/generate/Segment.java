package tpietzsch.shadergen.generate;

import java.util.Map;

public class Segment
{
	private final SegmentTemplate template;

	private final Map< String, String > keyToIdentifier;

	private String code = null;

	Segment( final SegmentTemplate template, final Map< String, String > keyToIdentifier )
	{
		this.template = template;
		this.keyToIdentifier = keyToIdentifier;
	}

	public synchronized String getCode()
	{
		if ( code == null )
			code = template.render( keyToIdentifier );
		return code;
	}

	public synchronized Segment bind( final String key, final Segment segment, final String segmentKey )
	{
		if ( code != null )
			throw new IllegalStateException( "trying to bind identifiers after code has been already generated." );
		keyToIdentifier.put( key, segment.getIdentifier( segmentKey ) );
		return this;
	}

	String getIdentifier( final String key )
	{
		final String s = keyToIdentifier.get( key );
		if ( s == null )
			throw new IllegalArgumentException( "Key '" + key + "' does not exist.");
		return s;
	}

	Map< String, String > getKeyToIdentifierMap()
	{
		return keyToIdentifier;
	}

}
