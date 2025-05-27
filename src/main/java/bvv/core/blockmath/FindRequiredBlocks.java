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
package bvv.core.blockmath;

import bvv.core.util.MatrixMath;
import java.util.ArrayList;

import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.algorithm.kdtree.HyperPlane;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.iterator.LocalizingIntervalIterator;
import net.imglib2.util.LinAlgHelpers;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

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
	 * Given an interval split into regular blocks of size {@code blockSize}.
	 * Which blocks are contained in the specified clipping volume?
	 *
	 * @param clip in voxel coordinates
	 * @param blockSize
	 * @param gridMin
	 * @param gridMax
	 * @return
	 */
	public static RequiredBlocks getRequiredBlocks(
			final ConvexPolytope clip,
			final int[] blockSize,
			final long[] gridMin,
			final long[] gridMax )
	{
		final int n = clip.numDimensions();
		final RequiredBlocks required = new RequiredBlocks( n );

		final ConvexPolytope shrunkClip = shrinkClippingPolytope( clip, blockSize );
//		System.out.println( "shrunkClip = " + GeomUtils.toString( shrunkClip ) );
		final ConvexPolytope gridClip = scaleClippingPolytope( shrunkClip, blockSize );
//		System.out.println( "gridClip = " + GeomUtils.toString( gridClip ) );

		// stupid implementation for now: check all planes for all cells...
		// TODO: make this more clever -- clip nTree
		final IntervalIterator gridIter = new LocalizingIntervalIterator( gridMin, gridMax );
		while( gridIter.hasNext() )
		{
			gridIter.fwd();
			if ( GeomUtils.isInside( gridClip, gridIter ) )
				required.add( gridIter );
		}

		return required;
	}

	private static HyperPlane sourceHyperPlane( Matrix4fc sourceToNDCTransposed, double nx, double ny, double nz, double d )
	{
		return MatrixMath.hyperPlane( new Vector4f( ( float ) nx, ( float ) ny, ( float ) nz, ( float ) -d ).mul( sourceToNDCTransposed ).normalize3() );
	}

	/**
	 * Backproject NDC {@code (-1,-1,-1) ... (1,1,1)} to source image and find overlapping blocks.
	 *
	 * @param levelToNDC
	 * 		Projection * View * Model * Upscale matrix
	 * @param blockSize
	 * @param gridMin
	 * @param gridMax
	 *
	 * @return
	 */
	public static RequiredBlocks getRequiredLevelBlocksFrustum(
			final Matrix4fc levelToNDC,
			final int[] blockSize,
			final long[] gridMin,
			final long[] gridMax )
	{
		final Matrix4f T = levelToNDC.transpose( new Matrix4f() );

		// planes bounding the view frustum, normals facing inwards, transformed to source coordinates
		final ConvexPolytope sourceRegion = new ConvexPolytope(
				sourceHyperPlane( T,  1,  0,  0, -1 ),
				sourceHyperPlane( T, -1,  0,  0, -1 ),
				sourceHyperPlane( T,  0,  1,  0, -1 ),
				sourceHyperPlane( T,  0, -1,  0, -1 ),
				sourceHyperPlane( T,  0,  0,  1, -1 ),
				sourceHyperPlane( T,  0,  0, -1, -1 ) );

		return getRequiredBlocks( sourceRegion, blockSize, gridMin, gridMax );
	}
}
