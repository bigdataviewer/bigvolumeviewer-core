package bvv.examples;

import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import bvv.util.BvvSource;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

public class Example04
{
	/**
	 * Add multiple sources to same viewer
	 */
	public static void main( final String[] args )
	{
		final ImagePlus imp = IJ.openImage( "https://imagej.nih.gov/ij/images/t1-head.zip" );
		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );

		final BvvSource source1 = BvvFunctions.show( img, "t1-head" );
		source1.setDisplayRange( 0, 555 );
		source1.setColor( new ARGBType( 0xff88ff88 ) );

		// BvvOptions.addTo(...) adds volume to existing window
		final BvvSource source2 = BvvFunctions.show( Views.translate( img, 100, 0, 0 ), "view", Bvv.options().addTo( source1 ) );
		source2.setDisplayRange( 0, 555 );
		source2.setColor( new ARGBType( 0xffff8888 ) );

		// BvvOptions.sourceTransform() to translate volume etc
		AffineTransform3D t = new AffineTransform3D();
		t.rotate( 1, -1 );
		t.translate( 150, -100, -80 );
		final BvvSource source3 = BvvFunctions.show( img, "transformed", Bvv.options().addTo( source1 ).sourceTransform( t ) );
		source3.setDisplayRange( 0, 555 );
		source3.setColor( new ARGBType( 0xff8888ff ) );
	}
}
