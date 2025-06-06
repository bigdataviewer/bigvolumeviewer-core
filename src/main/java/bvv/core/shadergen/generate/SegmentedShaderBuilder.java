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

import java.util.HashMap;
import java.util.Map;

public class SegmentedShaderBuilder
{
	static final Object NOT_UNIQUE = new Object();

	// maps uniform name to either NOT_UNIQUE or instantiated identifier (String
	private final Map< String, Object > uniforms = new HashMap<>();

	private final StringBuilder vpCode = new StringBuilder();

	private final StringBuilder fpCode = new StringBuilder();

	private void add( final Segment segment, final StringBuilder code )
	{
		final Map< String, SegmentTemplate.Identifier > map = segment.getKeyToIdentifierMap();
		map.forEach( ( name, identifier ) -> {
			uniforms.compute( name, ( n, value ) -> {
				if ( value == null && !identifier.isList() )
					return ( String ) identifier.value();
				else
					return NOT_UNIQUE;
			} );
		} );

		code.append( segment.getCode() );
	}

	public SegmentedShaderBuilder fragment( final Segment segment )
	{
		add( segment, fpCode );
		return this;
	}

	public SegmentedShaderBuilder vertex( final Segment segment )
	{
		add( segment, vpCode );
		return this;
	}

	public SegmentedShader build()
	{
		return new SegmentedShader( vpCode, fpCode, uniforms );
	}
}
