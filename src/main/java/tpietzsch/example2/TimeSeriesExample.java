package tpietzsch.example2;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.util.AxisOrder;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import bvv.util.BvvSource;
import bvv.util.BvvStackSource;
import ij.IJ;
import ij.ImagePlus;
import java.util.List;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class TimeSeriesExample
{
	public static void main( final String[] args ) throws SpimDataException
	{
		final ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/data/mitosis.tif" );
		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );

		final double pw = imp.getCalibration().pixelWidth;
		final double ph = imp.getCalibration().pixelHeight;
		final double pd = imp.getCalibration().pixelDepth;

		final BvvStackSource< UnsignedShortType > mitosis = BvvFunctions.show( img, "mitosis", Bvv.options()
				.axisOrder( AxisOrder.XYCZT )
				.sourceTransform( pw, ph, pd ) );

		mitosis.getConverterSetups().get( 0 ).setColor( new ARGBType( 0xffff0000 ) );
		mitosis.getConverterSetups().get( 0 ).setDisplayRange( 1582, 11086 );
		mitosis.getConverterSetups().get( 1 ).setColor( new ARGBType( 0xff00ff00 ) );
		mitosis.getConverterSetups().get( 1 ).setDisplayRange( 1614, 15787 );
	}
}
