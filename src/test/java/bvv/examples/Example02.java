package bvv.examples;

import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;

public class Example02
{
	/**
	 * Show RGB volume.
	 */
	public static void main( final String[] args )
	{
		final ImagePlus imp = IJ.openImage( "https://imagej.nih.gov/ij/images/flybrain.zip" );
		final Img< ARGBType > img = ImageJFunctions.wrapRGBA( imp );

		// additional Bvv.options() to specify calibration
		BvvFunctions.show( img, "flybrain",
				Bvv.options().sourceTransform(
						imp.getCalibration().pixelWidth,
						imp.getCalibration().pixelHeight,
						imp.getCalibration().pixelDepth	) );
	}
}
