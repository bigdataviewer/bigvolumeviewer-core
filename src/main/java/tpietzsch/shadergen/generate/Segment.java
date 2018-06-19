package tpietzsch.shadergen.generate;

import java.util.Map;

public class Segment
{
	private final String code;

	private final Map< String, String > keyToIdentifier;

	public Segment( final String code, final Map< String, String > keyToIdentifier )
	{
		this.code = code;
		this.keyToIdentifier = keyToIdentifier;
	}

	public String getCode()
	{
		return code;
	}

	public String getIdentifier( final String key )
	{
		final String s = keyToIdentifier.get( key );
		if ( s == null )
			throw new IllegalArgumentException( "Key '" + key + "' does not exist.");
		return s;
	}

	public Map< String, String > getKeyToIdentifierMap()
	{
		return keyToIdentifier;
	}
}
