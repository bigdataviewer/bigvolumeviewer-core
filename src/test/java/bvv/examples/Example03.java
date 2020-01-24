package bvv.examples;

import bdv.util.AxisOrder;
import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import bvv.util.BvvStackSource;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class Example03
{
	/**
	 * Show 16-bit 2-channel time-series.
	 */
	public static void main( final String[] args )
	{
		final ImagePlus imp = IJ.openImage( "https://imagej.nih.gov/ij/images/Spindly-GFP.zip" );
		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );

		final double pw = imp.getCalibration().pixelWidth;
		final double ph = imp.getCalibration().pixelHeight;
		final double pd = imp.getCalibration().pixelDepth;

		// additional Bvv.options() to specify axis order and calibration
		final BvvStackSource< UnsignedShortType > mitosis = BvvFunctions.show( img, "mitosis", Bvv.options()
				.axisOrder( AxisOrder.XYCZT )
				.sourceTransform( pw, ph, pd ) );

		// setting the color and min/max of channel 0
		mitosis.getConverterSetups().get( 0 ).setColor( new ARGBType( 0xffff0000 ) );
		mitosis.getConverterSetups().get( 0 ).setDisplayRange( 1582, 11086 );

		// setting the color and min/max of channel 1
		mitosis.getConverterSetups().get( 1 ).setColor( new ARGBType( 0xff00ff00 ) );
		mitosis.getConverterSetups().get( 1 ).setDisplayRange( 1614, 15787 );
	}
}
