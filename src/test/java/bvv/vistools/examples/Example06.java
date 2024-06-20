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
package bvv.vistools.examples;

import bdv.util.volatiles.VolatileViews;
import bvv.vistools.Bvv;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvStackSource;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class Example06
{
	/**
	 * Shows how CachedCellImgs are picked up as "MultiResolution" sources,
	 * that correctly handle Volatile types and missing data.
	 */
	public static void main( final String[] args )
	{
		final ImagePlus imp = IJ.openImage( "https://imagej.net/ij/images/flybrain.zip" );
		final RandomAccessibleInterval< ARGBType > flybrain = ImageJFunctions.wrapRGBA( imp );

		AffineTransform3D transform = new AffineTransform3D();
		final double sx = imp.getCalibration().pixelWidth;
		final double sy = imp.getCalibration().pixelHeight;
		final double sz = imp.getCalibration().pixelDepth;
		transform.set(
				sx, 0, 0, 0,
				0, sy, 0, 0,
				0, 0, sz, 0 );
		final UnsignedShortType type = new UnsignedShortType();

		// extract the red channel
		final RandomAccessibleInterval< UnsignedShortType > red = Converters.convert( flybrain, ( i, o ) -> o.set( ARGBType.red( i.get() ) ), type );

		// set up two cached CellImages that (lazily) convolve red with different sigmas
		final RandomAccessible< UnsignedShortType > source = Views.extendBorder( red );
		final double[] sigma1 = new double[] { 5, 5, 5 };
		final double[] sigma2 = new double[] { 4, 4, 4 };
		final long[] dimensions = Intervals.dimensionsAsLongArray( flybrain );
		final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory(
				ReadOnlyCachedCellImgOptions.options().cellDimensions( 32, 32, 32 ) );
		final Img< UnsignedShortType > gauss1 = factory.create( dimensions, type, cell -> {
			Gauss3.gauss( sigma1, source, cell, 1 );
			Thread.sleep( 30 );
		} );
		final Img< UnsignedShortType > gauss2 = factory.create( dimensions, type, cell -> {
			Gauss3.gauss( sigma2, source, cell, 1 );
			Thread.sleep( 80 );
		} );

		// set up another cached CellImages that (lazily) computes the absolute difference of gauss1 and gauss2
		final Img< UnsignedShortType > diff = factory.create( dimensions, type, cell -> LoopBuilder.setImages(
						Views.interval( gauss1, cell ),
						Views.interval( gauss2, cell ),
						cell )
					.forEachPixel( ( in1, in2, out ) -> out.set( 10 * Math.abs( in1.get() - in2.get() ) ) ) );

		// show red channel
		final BvvStackSource< UnsignedShortType > sourceRed = BvvFunctions.show( red,
				"red", Bvv.options().sourceTransform( transform ) );
		sourceRed.setDisplayRange( 0, 255 );
		sourceRed.setDisplayRangeBounds( 0, 255 );
		sourceRed.setColor( new ARGBType( 0xffff0000 ) );

		// add gauss1, wrapped as volatile
		final BvvStackSource< ? > sourceGauss1 = BvvFunctions.show( VolatileViews.wrapAsVolatile( gauss1 ),
				"gauss1", Bvv.options().sourceTransform( transform ).addTo( sourceRed ) );
		sourceGauss1.setDisplayRange( 0, 60 );
		sourceGauss1.setDisplayRangeBounds( 0, 255 );
		sourceGauss1.setColor( new ARGBType( 0xff00ff00 ) );

		// add gauss2, wrapped as volatile
		final BvvStackSource< ? > sourceGauss2 = BvvFunctions.show( VolatileViews.wrapAsVolatile( gauss2 ),
				"gauss2", Bvv.options().sourceTransform( transform ).addTo( sourceRed ) );
		sourceGauss2.setDisplayRange( 0, 60 );
		sourceGauss2.setDisplayRangeBounds( 0, 255 );
		sourceGauss2.setColor( new ARGBType( 0xff0000ff ) );

		// add diff, wrapped as volatile and shifted to the right
		transform.translate( 600, 0, 0 );
		final BvvStackSource< ? > sourceDiff = BvvFunctions.show( VolatileViews.wrapAsVolatile( diff ),
				"diff", Bvv.options().sourceTransform( transform ).addTo( sourceRed ) );
		sourceDiff.setDisplayRange( 0, 255 );
		sourceDiff.setDisplayRangeBounds( 0, 255 );

		// add original RGB image, shifted to the left
		transform.translate( -1200, 0, 0 );
		final BvvStackSource< ? > sourceFlybrain = BvvFunctions.show( flybrain,
				"flybrain", Bvv.options().sourceTransform( transform ).addTo( sourceRed ) );
	}
}
