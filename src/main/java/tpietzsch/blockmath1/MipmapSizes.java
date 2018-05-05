package tpietzsch.blockmath1;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class MipmapSizes
{
	private final Vector3f pNear = new Vector3f();
	private final Vector3f pFarMinusNear = new Vector3f();

	private float sn;
	private float sf;
	private float v0x;
	private float v0y;
	private float v0z;
	private float drels;

	/**
	 * @param sourceToNDC
	 * 		Projection * View * Model matrix@param viewportWidth
	 * @param viewportWidth
	 */
	public void init( final Matrix4fc sourceToNDC, final int viewportWidth )
	{
		Matrix4f NDCtoSource = sourceToNDC.invert( new Matrix4f() );
		final float w = 2f / viewportWidth;
		sn = NDCtoSource.transformProject( w, 0, -1, new Vector3f() ).sub( NDCtoSource.transformProject( 0, 0, -1, new Vector3f() ) ).length();
		sf = NDCtoSource.transformProject( w, 0, 1, new Vector3f() ).sub( NDCtoSource.transformProject( 0, 0, 1, new Vector3f() ) ).length();

		sourceToNDC.unprojectRay( 0.5f, 0.5f, new int[] { 0, 0, 1, 1 }, pNear, pFarMinusNear );
		Vector3f dir = pFarMinusNear.normalize( new Vector3f() );
		drels = 1f / pFarMinusNear.lengthSquared();
		v0x = ( float ) Math.sqrt( 1.0 - dir.dot( 1, 0, 0 ) );
		v0y = ( float ) Math.sqrt( 1.0 - dir.dot( 0, 1, 0 ) );
		v0z = ( float ) Math.sqrt( 1.0 - dir.dot( 0, 0, 1 ) );
	}

	/**
	 * @param levelScaleFactors
	 * 		scale factors from requested resolution level to full resolution
	 */
	public float sl( final int[] levelScaleFactors )
	{
		final int x = levelScaleFactors[ 0 ];
		final int y = levelScaleFactors[ 1 ];
		final int z = levelScaleFactors[ 2 ];
		return Math.max( x * v0x, Math.max( y * v0y, z * v0z ) );
	}

	public int bestLevel( float[] sls, Vector3fc x, Vector3f temp )
	{

		final float drel = x.sub( pNear, temp ).dot( pFarMinusNear ) * drels;
		final float sd = drel * sf + ( 1 - drel ) * sn;

		for ( int l = 0; l < sls.length; ++l )
		{
			if ( sd <= sls[ l ] )
			{
				if ( l == 0 )
					return 0;
				return ( sls[ l ] - sd < sd - sls[ l - 1 ] ) ? l : ( l - 1 );
			}
		}
		return sls.length - 1;
	}

	public float getSn()
	{
		return sn;
	}

	public float getSf()
	{
		return sf;
	}
}
