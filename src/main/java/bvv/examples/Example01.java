package bvv.examples;

import bvv.util.BvvFunctions;
import bvv.util.BvvSource;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class Example01
{
	/**
	 * Show 16-bit volume.
	 */
	public static void main( final String[] args )
	{
		final ImagePlus imp = IJ.openImage( "https://imagej.nih.gov/ij/images/t1-head.zip" );
		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );

		final BvvSource source = BvvFunctions.show( img, "t1-head" );

		// source handle can be used to set color, display range, visibility, ...
		source.setDisplayRange( 0, 555 );
	}
}
