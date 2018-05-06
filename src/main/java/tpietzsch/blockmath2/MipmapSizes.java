package tpietzsch.blockmath2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.algorithm.kdtree.HyperPlane;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NoFeasibleSolutionException;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import tpietzsch.util.MatrixMath;

public class MipmapSizes
{
	final Vector3f pNear = new Vector3f();
	final Vector3f pFarMinusNear = new Vector3f();

	private float sn;
	private float sf;
	private float v0x;
	private float v0y;
	private float v0z;
	private float drels;
	private float[] sls;
	private float drelClosestSourcePoint;
	private int baseLevel;
	private boolean isVisible;

	private final Vector3f dir = new Vector3f();

	/**
	 * @param sourceToNDC
	 * 		Projection * View * Model matrix@param viewportWidth
	 * @param viewportWidth
	 * @param raiLevels
	 */
	public void init( final Matrix4fc sourceToNDC, final int viewportWidth, final List< RaiLevel > raiLevels )
	{
		final Matrix4f NDCtoSource = sourceToNDC.invert( new Matrix4f() );
		final float w = 2f / viewportWidth;
		// viewport pixel width on near and far plane (in source coordinates)
		sn = NDCtoSource.transformProject( w, 0, -1, new Vector3f() ).sub( NDCtoSource.transformProject( 0, 0, -1, new Vector3f() ) ).length();
		sf = NDCtoSource.transformProject( w, 0, 1, new Vector3f() ).sub( NDCtoSource.transformProject( 0, 0, 1, new Vector3f() ) ).length();

		NDCtoSource.unprojectInvRay( 0.5f, 0.5f, new int[] { 0, 0, 1, 1 }, pNear, pFarMinusNear );
		pFarMinusNear.normalize( dir );
		drels = 1f / pFarMinusNear.lengthSquared();
		// voxel size on near plane (in source coordinates)
		v0x = ( float ) Math.sqrt( 1.0 - dir.dot( 1, 0, 0 ) );
		v0y = ( float ) Math.sqrt( 1.0 - dir.dot( 0, 1, 0 ) );
		v0z = ( float ) Math.sqrt( 1.0 - dir.dot( 0, 0, 1 ) );

		sls = new float[ raiLevels.size() ];
		for ( int i = 0; i < raiLevels.size(); i++ )
		{
			RaiLevel raiLevel = raiLevels.get( i );
			sls[ i ] = sl( raiLevel.r );
		}

		/*
		 * Closest visible source point to near clipping plane.
		 * TODO: Solving this with LP simplex seems a bit insane. Is there a more closed-form solution???
		 */
		final long[] imgSize = new long[ 3 ];
		raiLevels.get( 0 ).rai.dimensions( imgSize );
		final Matrix4f T = sourceToNDC.transpose( new Matrix4f() );
		final ConvexPolytope sourceRegion = new ConvexPolytope(
				// planes bounding the view frustum, normals facing inwards, transformed to source coordinates
				sourceHyperPlane( T, 1, 0, 0, -1 ),
				sourceHyperPlane( T, -1, 0, 0, -1 ),
				sourceHyperPlane( T, 0, 1, 0, -1 ),
				sourceHyperPlane( T, 0, -1, 0, -1 ),
				sourceHyperPlane( T, 0, 0, 1, -1 ),
				sourceHyperPlane( T, 0, 0, -1, -1 ),
				// planes bounding the source, normals facing inwards
				new HyperPlane( 1, 0, 0, 0 ), // TODO: 0.5 offsets?
				new HyperPlane( 0, 1, 0, 0 ), // TODO: 0.5 offsets?
				new HyperPlane( 0, 0, 1, 0 ), // TODO: 0.5 offsets?
				new HyperPlane( -1, 0, 0, -imgSize[ 0 ] ), // TODO: 0.5 offsets?
				new HyperPlane( 0, -1, 0, -imgSize[ 1 ] ), // TODO: 0.5 offsets?
				new HyperPlane( 0, 0, -1, -imgSize[ 2 ] ) ); // TODO: 0.5 offsets?

		pFarMinusNear.mul( drels, dir );
		LinearObjectiveFunction f = new LinearObjectiveFunction( new double[] { dir.x(), dir.y(), dir.z() }, -dir.dot( pNear ) );
		List< LinearConstraint > constraints = new ArrayList<>();
		for ( HyperPlane plane : sourceRegion.getHyperplanes() )
			constraints.add( new LinearConstraint( plane.getNormal(), Relationship.GEQ, plane.getDistance() ) );
		try
		{
			PointValuePair sln = new SimplexSolver().optimize( f, new LinearConstraintSet( constraints ), GoalType.MINIMIZE );
			drelClosestSourcePoint = Math.max( Math.min( sln.getValue().floatValue(), 1.0f ), 0.0f );
		}
		catch ( NoFeasibleSolutionException e )
		{
			isVisible = false;
		}

		baseLevel = bestLevel( drelClosestSourcePoint );
	}

	private static HyperPlane sourceHyperPlane( Matrix4fc sourceToNDCTransposed, double nx, double ny, double nz, double d )
	{
		return MatrixMath.hyperPlane( new Vector4f( ( float ) nx, ( float ) ny, ( float ) nz, ( float ) -d ).mul( sourceToNDCTransposed ).normalize3() );
	}

	/**
	 * Is any part of the volume visible?
	 */
	public boolean isVisible()
	{
		return isVisible;
	}

	/**
	 * Highest required resolution (lowest level) for any visible block
	 */
	public int getBaseLevel()
	{
		return baseLevel;
	}

	/**
	 * @param levelScaleFactors
	 * 		scale factors from requested resolution level to full resolution
	 */
	private float sl( final int[] levelScaleFactors )
	{
		final int x = levelScaleFactors[ 0 ];
		final int y = levelScaleFactors[ 1 ];
		final int z = levelScaleFactors[ 2 ];
		return Math.max( x * v0x, Math.max( y * v0y, z * v0z ) );
	}

	public int bestLevel( Vector3fc x, Vector3f temp )
	{
		final float drel = x.sub( pNear, temp ).dot( pFarMinusNear ) * drels;
		return bestLevel( drel );
	}

	private int bestLevel( final float drel )
	{
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


	// DEBUG...

	public float getDrel( Vector3fc x, Vector3f temp )
	{
		final float drel = x.sub( pNear, temp ).dot( pFarMinusNear ) * drels;
		return drel;
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
