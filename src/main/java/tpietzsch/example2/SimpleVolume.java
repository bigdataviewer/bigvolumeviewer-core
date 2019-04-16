package tpietzsch.example2;

import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.multires.SimpleStack3D;
import tpietzsch.util.MatrixMath;

/**
 * TODO
 */
public class SimpleVolume
{
	private final SimpleStack3D< ? > simpleStack;

	private final VolumeTextureU16 texture;

	private final Matrix4f ims;

	/**
	 * @param simpleStack single-channel source
	 */
	public SimpleVolume( final SimpleStack3D< ? > simpleStack )
	{
		this.simpleStack = simpleStack;
		this.texture = new VolumeTextureU16();
		this.ims = MatrixMath.affine( simpleStack.getSourceTransform(), new Matrix4f() ).invert();
	}

	public SimpleStack3D< ? > getSimpleStack()
	{
		return simpleStack;
	}

	public VolumeTextureU16 getVolumeTexture()
	{
		return texture;
	}

	public Matrix4f getIms()
	{
		return ims;
	}

	/**
	 * Get the size of a voxel in world coordinates.
	 * Take a source voxel (0,0,0)-(1,1,1) and transform it to world coordinates.
	 * Take the minimum of the transformed voxels edge lengths.
	 */
	public double getVoxelSizeInWorldCoordinates()
	{
		final AffineTransform3D sourceToWorld = simpleStack.getSourceTransform();

		final double[] tzero = new double[ 3 ];
		sourceToWorld.apply( new double[ 3 ], tzero );

		final double[] one = new double[ 3 ];
		final double[] tone = new double[ 3 ];
		double voxelSize = Double.POSITIVE_INFINITY;
		for ( int i = 0; i < 3; ++i )
		{
			for ( int d = 0; d < 3; ++d )
				one[ d ] = d == i ? 1 : 0;
			sourceToWorld.apply( one, tone );
			LinAlgHelpers.subtract( tone, tzero, tone );
			voxelSize = Math.min( voxelSize, LinAlgHelpers.length( tone ) );
		}
		return voxelSize;
	}

	public Vector3f getSourceMin()
	{
		final Interval lbb = simpleStack.getImage();
		final Vector3f sourceMin = new Vector3f( lbb.min( 0 ), lbb.min( 1 ), lbb.min( 2 ) );
		return sourceMin;
	}

	public Vector3f getSourceMax()
	{
		final Interval lbb = simpleStack.getImage();
		final Vector3f sourceMax = new Vector3f( lbb.max( 0 ), lbb.max( 1 ), lbb.max( 2 ) );
		return sourceMax;
	}

	public void upload( final JoglGpuContext context )
	{
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		System.out.println( "SimpleVolume.upload" );
		texture.init( Intervals.dimensionsAsIntArray( simpleStack.getImage() ) );
		texture.setRAI( ( RandomAccessibleInterval< UnsignedShortType > ) simpleStack.getImage() );
		texture.upload( context );
	}
}
