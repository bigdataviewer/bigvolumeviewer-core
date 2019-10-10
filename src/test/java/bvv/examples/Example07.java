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
import org.joml.Matrix4f;
import tpietzsch.example2.VolumeViewerPanel;
import tpietzsch.scene.TexturedUnitCube;

public class Example07
{
	/**
	 * ImgLib2 :-)
	 */
	public static void main( final String[] args )
	{
		final ImagePlus imp = IJ.openImage( "https://imagej.nih.gov/ij/images/t1-head.zip" );
		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );

		final BvvSource source = BvvFunctions.show( img, "t1-head",
				Bvv.options().maxAllowedStepInVoxels( 0 ).renderWidth( 1024 ).renderHeight( 1024 ).preferredSize( 1024, 1024 ) );
		source.setDisplayRange( 0, 800 );
		source.setColor( new ARGBType( 0xffff8800 ) );

		final TexturedUnitCube cube = new TexturedUnitCube( "imglib2.png" );
		final VolumeViewerPanel viewer = source.getBvvHandle().getViewerPanel();
		viewer.setRenderScene( ( gl, data ) -> {
			final Matrix4f cubetransform = new Matrix4f().translate( 140, 150, 65 ).scale( 80 );
			cube.draw( gl, new Matrix4f( data.getPv() ).mul( cubetransform ) );
		} );

		viewer.requestRepaint();
	}
}
