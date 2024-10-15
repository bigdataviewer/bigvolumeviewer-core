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
package bvv.core.blockmath;

import java.util.Iterator;

import net.imglib2.RealLocalizable;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.algorithm.kdtree.HyperPlane;
import net.imglib2.util.LinAlgHelpers;

public class GeomUtils
{
	public static double signedDistance( final HyperPlane plane, final double[] point )
	{
		return LinAlgHelpers.dot( point, plane.getNormal() ) - plane.getDistance();
	}

	public static double signedDistance( final HyperPlane plane, final RealLocalizable point )
	{
		final int n = plane.numDimensions();
		final double[] normal = plane.getNormal();
		double sum = 0;
		for ( int d = 0; d < n; ++d )
			sum += normal[ d ] * point.getDoublePosition( d );
		return sum - plane.getDistance();
	}

	public static boolean isAbove( final HyperPlane plane, final double[] point )
	{
		return signedDistance( plane, point ) >= 0;
	}

	public static boolean isAbove( final HyperPlane plane, final RealLocalizable point )
	{
		return signedDistance( plane, point ) >= 0;
	}

	public static boolean isInside( final ConvexPolytope poly, final double[] point )
	{
		for (final HyperPlane plane : poly.getHyperplanes() )
			if ( !isAbove( plane, point) )
				return false;
		return true;
	}

	public static boolean isInside( final ConvexPolytope poly, final RealLocalizable point )
	{
		for (final HyperPlane plane : poly.getHyperplanes() )
			if ( !isAbove( plane, point) )
				return false;
		return true;
	}

	public static String toString( final HyperPlane plane )
	{
		final StringBuilder sb = new StringBuilder( "( " );
		final double[] normal = plane.getNormal();
		for ( int d = 0; d < normal.length; ++d )
			sb.append( String.format( "%.3f, ", normal[ d ] ) );
		sb.append( String.format( "%.3f )", plane.getDistance() ) );
		return sb.toString();
	}

	public static String toString( final ConvexPolytope polytope )
	{
		final StringBuilder sb = new StringBuilder( "{ " );
		final Iterator< ? extends HyperPlane > iter = polytope.getHyperplanes().iterator();
		while( iter.hasNext() )
		{
			sb.append( toString( iter.next() ) );
			if ( iter.hasNext() )
				sb.append( ",\n  " );
		}
		sb.append( "}" );
		return sb.toString();
	}
}
