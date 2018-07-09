package tpietzsch.shadergen.generate;

import java.util.Map;
import tpietzsch.shadergen.generate.SegmentTemplate.Identifier;

public class Segment
{
	private final SegmentTemplate template;

	private final Map< String, Identifier > keyToIdentifier;

	private String code = null;

	Segment( final SegmentTemplate template, final Map< String, Identifier > keyToIdentifier )
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

	public synchronized Segment bind( final String key, final int index, final Segment segment, final String segmentKey )
	{
		if ( code != null )
			throw new IllegalStateException( "trying to bind identifiers after code has been already generated." );
		Identifier identifier = keyToIdentifier.get( key );
		if ( !identifier.isList() )
		{
			identifier = new Identifier();
			keyToIdentifier.put( key, identifier );
		}
		final Identifier value = segment.getIdentifier( segmentKey );
		if ( value.isList() )
			throw new IllegalArgumentException( "Key '" + key + "' in the segment maps to a list of identifiers. Expected single identifier." );
		identifier.put( index, ( String ) value.value() );
		return this;
	}

	public synchronized Segment repeat( final String key, final int num )
	{
		keyToIdentifier.put( key, SegmentTemplate.proposeIdentifiers( key, num ) );
		return this;
	}

	Identifier getIdentifier( final String key )
	{
		final Identifier identifier = keyToIdentifier.get( key );
		if ( identifier == null )
			throw new IllegalArgumentException( "Key '" + key + "' does not exist." );
		return identifier;
	}

	String getSingleIdentifier( final String key )
	{
		final Identifier identifier = keyToIdentifier.get( key );
		if ( identifier == null )
			throw new IllegalArgumentException( "Key '" + key + "' does not exist." );
		if ( identifier.isList() )
			throw new IllegalArgumentException( "Key '" + key + "' maps to a list of identifiers. Expected single identifier." );
		return ( String ) identifier.value();
	}

	Map< String, Identifier > getKeyToIdentifierMap()
	{
		return keyToIdentifier;
	}

}
