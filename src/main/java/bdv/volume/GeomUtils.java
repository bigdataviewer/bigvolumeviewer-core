package bdv.volume;

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
