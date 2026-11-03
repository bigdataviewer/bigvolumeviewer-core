/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2026 Tobias Pietzsch
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
package bvv.debug;

import bdv.util.volatiles.VolatileViews;
import bvv.vistools.Bvv;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;
import bvv.vistools.BvvStackSource;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class TestHalfPixel
{
	public static void main( final String[] args )
	{
		Bvv bvv = BvvFunctions.show( BvvOptions.options().frameTitle( "Test half pixel" ).ditherWidth( 0 ).maxAllowedStepInVoxels( 0.05 ) );

		int nEdge = 3;
		final RandomAccessibleInterval< ? > rai = makeSimpleRAI(nEdge);
		final RandomAccessibleInterval< ? > cachedRai = makeCachedCellImg(nEdge);

		BvvStackSource< ? > raiCS = BvvFunctions.show( rai,
				"simple RAI",
				BvvOptions.options().addTo( bvv ));
		AffineTransform3D t = new AffineTransform3D();
		t.translate( nEdge, 0.0, 0.0 );

		BvvStackSource< ? > cachedRaiCS = BvvFunctions.show( VolatileViews.wrapAsVolatile(cachedRai),
				"cached RAI",
				BvvOptions.options().addTo( bvv ).sourceTransform( t ));
		AffineTransform3D viewT = bvv.getBvvHandle().getViewerPanel().state().getViewerTransform();
		viewT.scale( 0.5 );
		viewT.translate( 80., 140.0, 0.0 );
		bvv.getBvvHandle().getViewerPanel().state().setViewerTransform( viewT );
	}

	private static Img< UnsignedByteType > makeSimpleRAI( final int nEdge )
	{
		final Img< UnsignedByteType > img = ArrayImgs.unsignedBytes( nEdge, nEdge, nEdge );
		img.forEach( s -> s.set( 255 ) );
		return img;
	}

	private static Img< UnsignedByteType > makeCachedCellImg( final int nEdge )
	{
		final long[] dims = new long[] { nEdge, nEdge, nEdge };
		final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(
				ReadOnlyCachedCellImgOptions.options().cellDimensions( 32 ) );
		return factory.create( dims, new UnsignedByteType(), cell -> cell.forEach( s -> s.set( 255 ) ) );
	}
}
