/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2024 Tobias Pietzsch
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
package bvv.core.shadergen;

import net.imglib2.RealInterval;

import org.joml.Vector3fc;

public interface Uniform3f
{
	void set( float v0, float v1, float v2 );

	/*
	 * DEFAULT METHODS
	 */

	default void set( final Vector3fc v )
	{
		set( v.x(), v.y(), v.z() );
	}

	default void set( final RealInterval interval, final MinMax minmax )
	{
		if ( interval.numDimensions() < 3 )
			throw new IllegalArgumentException(
					"Interval has " + interval.numDimensions() + " dimensions."
							+ "Expected interval of at least dimension 3." );

		if ( minmax == MinMax.MIN )
			set(
					( float ) interval.realMin( 0 ),
					( float ) interval.realMin( 1 ),
					( float ) interval.realMin( 2 ) );
		else
			set(
					( float ) interval.realMax( 0 ),
					( float ) interval.realMax( 1 ),
					( float ) interval.realMax( 2 ) );
	}
}
