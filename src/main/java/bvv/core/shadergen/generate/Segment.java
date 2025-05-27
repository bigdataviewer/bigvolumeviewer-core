/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.shadergen.generate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Segment
{
	private final SegmentTemplate template;

	private final Map< String, SegmentTemplate.Identifier > keyToIdentifier;

	private String code = null;

	Segment( final SegmentTemplate template, final Map< String, SegmentTemplate.Identifier > keyToIdentifier )
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

	public Segment bind( final String key, final Segment segment )
	{
		return bind( key, segment, key );
	}

	public synchronized Segment bind( final String key, final int index, final Segment segment, final String segmentKey )
	{
		if ( code != null )
			throw new IllegalStateException( "trying to bind identifiers after code has been already generated." );
		SegmentTemplate.Identifier identifier = keyToIdentifier.get( key );
		if ( !identifier.isList() )
		{
			identifier = new SegmentTemplate.Identifier();
			keyToIdentifier.put( key, identifier );
		}
		final SegmentTemplate.Identifier value = segment.getIdentifier( segmentKey );
		if ( value.isList() )
			throw new IllegalArgumentException( "Key '" + key + "' in the segment maps to a list of identifiers. Expected single identifier." );
		identifier.put( index, ( String ) value.value() );
		return this;
	}

	public Segment bind( final String key, final int index, final Segment segment )
	{
		return bind( key, index, segment, key );
	}

	public synchronized Segment repeat( final String key, final int num )
	{
		keyToIdentifier.put( key, SegmentTemplate.proposeIdentifiers( key, num ) );
		return this;
	}

	public synchronized Segment repeat( List< String > keys, final int num )
	{
		keys.forEach( k -> repeat( k, num ) );
		return this;
	}

	public synchronized void insert( final String key, final Segment ... segments )
	{
		insert( key, Arrays.asList( segments ) );
	}

	public synchronized void insert( final String key, final Collection< Segment > segments )
	{
		StringBuilder sb = new StringBuilder( "\n" );
		for ( Segment segment : segments )
			sb.append( segment.getCode() );
		keyToIdentifier.put( key, new SegmentTemplate.Identifier( sb.toString() ) );
	}

	SegmentTemplate.Identifier getIdentifier( final String key )
	{
		final SegmentTemplate.Identifier identifier = keyToIdentifier.get( key );
		if ( identifier == null )
			throw new IllegalArgumentException( "Key '" + key + "' does not exist." );
		return identifier;
	}

	String getSingleIdentifier( final String key )
	{
		final SegmentTemplate.Identifier identifier = getIdentifier( key );
		if ( identifier.isList() )
			throw new IllegalArgumentException( "Key '" + key + "' maps to a list of identifiers. Expected single identifier." );
		return ( String ) identifier.value();
	}

	Map< String, SegmentTemplate.Identifier > getKeyToIdentifierMap()
	{
		return keyToIdentifier;
	}
}
