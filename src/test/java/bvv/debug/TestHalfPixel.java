package bvv.debug;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import bdv.util.volatiles.VolatileViews;
import bvv.vistools.Bvv;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;
import bvv.vistools.BvvStackSource;
public class TestHalfPixel
{
	public static void main( final String[] args )
	{
		Bvv bvv = BvvFunctions.show( BvvOptions.options().frameTitle( "Test half pixel" ));
		UnsignedByteType type = new UnsignedByteType();
		final int nFillValue = 255;

		//UnsignedShortType type = new UnsignedShortType();
		//final int nFillValue = 65535;

		int nEdge = 3;
		//make a 5x5 constant intensity blocks
		final RandomAccessibleInterval< ? > rai = GenerateVolumes.makeSimpleRAI(type, nEdge, 0, nFillValue, true );
		final RandomAccessibleInterval< ? > cachedRai = GenerateVolumes.makeCachedCellImg(type, nEdge, 0, nFillValue, true );
		
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
}
