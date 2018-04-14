package bdv.volume;

import java.util.ArrayList;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.algorithm.kdtree.HyperPlane;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.iterator.LocalizingZeroMinIntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

public class FindRequiredBlocks
{
	/**
	 * Creates a new clipping polytope by shifting the planes of {@code clip}
	 * such that we only have to check the (0, 0) corner of cells.
	 * <p>
	 * <em>(0, 0)</em> is contained in the created polytope, if and only if the
	 * block <em>(-0.5, -0.5) .. (blockSize[0]-0.5, blockSize[1]-0.5)</em>
	 * overlaps the original clipping polytope {@code clip}.
	 * <p>
	 * Works for arbitrary number of dimensions.
	 *
	 * @param clip
	 * @param blockSize
	 * @return
	 */
	public static ConvexPolytope shrinkClippingPolytope(
			final ConvexPolytope clip,
			final int[] blockSize )
	{
		final int n = clip.numDimensions();

		final double[] cellBBMin = new double[ n ];
		final double[] cellBBMax = new double[ n ];
		for ( int d = 0; d < n; ++d )
		{
			cellBBMin[ d ] = -0.5;
			cellBBMax[ d ] = blockSize[ d ] - 0.5;
		}
		final double[] offset = new double[ n ];

		final ArrayList< HyperPlane > planes = new ArrayList<>();
		for ( final HyperPlane plane : clip.getHyperplanes() )
		{
			final double[] nn = plane.getNormal();
			final double m = plane.getDistance();
			for ( int d = 0; d < n; ++d )
				offset[ d ] = ( nn[ d ] < 0 ) ? cellBBMin[ d ] : cellBBMax[ d ];
			planes.add( new HyperPlane( nn, m - LinAlgHelpers.dot( nn, offset ) ) );
		}
		return new ConvexPolytope( planes );
	}

	/**
	 * Creates a clipping polytope in grid space from {@code clip} by scaling
	 * with inverse {@code blockSize}.
	 *
	 * @param clip
	 * @param blockSize
	 * @return
	 */
	public static ConvexPolytope scaleClippingPolytope(
			final ConvexPolytope clip,
			final int[] blockSize )
	{
		final int n = clip.numDimensions();
		final double[] nn = new double[ n ];
		final ArrayList< HyperPlane > planes = new ArrayList<>();
		for ( final HyperPlane plane : clip.getHyperplanes() )
		{
			final double[] normal = plane.getNormal();
			for ( int d = 0; d < n; ++d )
				nn[ d ] = normal[ d ] * blockSize[ d ];
			final double len = LinAlgHelpers.length( nn );
			for ( int d = 0; d < n; ++d )
				nn[ d ] /= len;
			final double m = plane.getDistance() / len;
			planes.add( new HyperPlane( nn, m ) );
		}
		return new ConvexPolytope( planes );
	}



	/**
	 * Given {@link RandomAccessibleInterval} split into regular blocks of size {@code blockSize}.
	 * Which blocks are contained in the specified clipping volume?
	 *
	 * Note: we assume that img starts at (0,0,0). (TODO)
	 *
	 * @param clip
	 * @param blockSize
	 * @param imgSize
	 * @return
	 */
	public static RequiredBlocks getRequiredBlocks(
			final ConvexPolytope clip,
			final int[] blockSize,
			final long[] imgSize )
	{
		final int n = clip.numDimensions();
		final RequiredBlocks required = new RequiredBlocks( n );

		final ConvexPolytope shrunkClip = shrinkClippingPolytope( clip, blockSize );
//		System.out.println( "shrunkClip = " + GeomUtils.toString( shrunkClip ) );
		final ConvexPolytope gridClip = scaleClippingPolytope( shrunkClip, blockSize );
//		System.out.println( "gridClip = " + GeomUtils.toString( gridClip ) );

		final long[] gridSize = new long[ n ];
		for ( int d = 0; d < n; ++d )
			gridSize[ d ] = ( imgSize[ d ] - 1 ) / blockSize[ d ] + 1;

		// stupid implementation for now: check all planes for all cells...
		// TODO: make this more clever -- clip nTree
		final IntervalIterator gridIter = new LocalizingZeroMinIntervalIterator( gridSize );
		while( gridIter.hasNext() )
		{
			gridIter.fwd();
			if ( GeomUtils.isInside( gridClip, gridIter ) )
				required.add( gridIter );
		}

		return required;
	}

	/**
	 *
	 * Note: we assume that img starts at (0,0,0). (TODO)
	 *
	 * @param sourceToScreen
	 * @param w
	 * @param h
	 * @param dd
	 * @param blockSize
	 * @param sourceSize
	 * @return
	 */
	public static RequiredBlocks getRequiredBlocksParallelProjection(
			final AffineTransform3D sourceToScreen,
			final int w,
			final int h,
			final int dd,
			final int[] blockSize,
			final long[] sourceSize )
	{
		// planes bounding the screen volume, normals facing inwards...
		final ConvexPolytope screenRegion = new ConvexPolytope(
				new HyperPlane(  0,  0,  1,   0 ),
				new HyperPlane(  0,  0, -1, -dd ),
				new HyperPlane(  1,  0,  0,   0 ),
				new HyperPlane( -1,  0,  0,  -w ),
				new HyperPlane(  0,  1,  0,   0 ),
				new HyperPlane(  0, -1,  0,  -h ) );
//		System.out.println( "screenRegion = " + GeomUtils.toString( screenRegion ) );

		// transform to source coordinates
		final ConvexPolytope sourceRegion = ConvexPolytope.transform( screenRegion, sourceToScreen.inverse() );
//		System.out.println( "sourceRegion = " + GeomUtils.toString( sourceRegion ) );

		return getRequiredBlocks( sourceRegion, blockSize, sourceSize );
	}


}
