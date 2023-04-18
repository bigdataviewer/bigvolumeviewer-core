package tpietzsch.scene.mesh;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import net.imagej.mesh.Mesh;
import net.imagej.mesh.Meshes;
import net.imagej.mesh.Triangles;
import net.imagej.mesh.Vertices;
import net.imagej.mesh.io.stl.STLMeshIO;
import net.imagej.mesh.naive.NaiveDoubleMesh;
import net.imagej.mesh.nio.BufferMesh;

public class MeshPlayground
{
	public static void main( String[] args )
	{
		String fn = "/Users/pietzsch/workspace/data/teapot.stl";
		Mesh mesh = null;
		try
		{
			NaiveDoubleMesh nmesh = new NaiveDoubleMesh();
			STLMeshIO meshIO = new STLMeshIO();
			meshIO.read( nmesh, new File( fn ) );
			mesh = new BufferMesh( ( int ) mesh.vertices().size(), ( int ) mesh.triangles().size(), true );
			Meshes.calculateNormals( nmesh, mesh );
//			mesh = toBufferMesh( nmesh );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		System.out.println( mesh.vertices().size() + " vertices" );
		System.out.println( mesh.triangles().size() + " triangles" );
	}

	public static BufferMesh load()
	{
		String fn = "/Users/pietzsch/workspace/data/teapot.stl";
		BufferMesh mesh = null;
		try
		{
			NaiveDoubleMesh nmesh = new NaiveDoubleMesh();
			STLMeshIO meshIO = new STLMeshIO();
			meshIO.read( nmesh, new File( fn ) );
			bbox( nmesh );
			mesh = calculateNormals( nmesh );
//			mesh = toBufferMesh( nmesh );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		bbox( mesh );
		return mesh;
	}

	static BufferMesh calculateNormals( Mesh mesh )
	{
		final int nvertices = ( int ) mesh.vertices().size();
		final int ntriangles = ( int ) mesh.triangles().size();
		final BufferMesh bufferMesh = new BufferMesh( nvertices, ntriangles, true );
		Meshes.calculateNormals( mesh, bufferMesh );
		return bufferMesh;
	}

	static void bbox( Mesh mesh )
	{
		final int nvertices = ( int ) mesh.vertices().size();

		final Vertices v = mesh.vertices();
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for ( int i = 0; i < nvertices; i++ )
		{
			final float x = v.xf( i );
			minX = Math.min( x, minX );
			maxX = Math.max( x, maxX );

			final float y = v.yf( i );
			minY = Math.min( y, minY );
			maxY = Math.max( y, maxY );

			final float z = v.zf( i );
			minZ = Math.min( z, minZ );
			maxZ = Math.max( z, maxZ );
		}

		System.out.println( Arrays.toString( new float[] { minX, maxX, minY, maxY, minZ, maxZ } ) );
	}

	static BufferMesh toBufferMesh( Mesh mesh )
	{
		if ( mesh instanceof BufferMesh )
			return (BufferMesh ) mesh;

		final int nvertices = ( int ) mesh.vertices().size();
		final int ntriangles = ( int ) mesh.triangles().size();
		final BufferMesh bufferMesh = new BufferMesh( nvertices, ntriangles, true );

		final Vertices bv = bufferMesh.vertices();
		final Vertices v = mesh.vertices();
		for ( int i = 0; i < nvertices; i++ )
			bv.addf( v.xf( i ), v.yf( i ), v.zf( i ), v.nxf( i ), v.nyf( i ), v.nzf( i ), v.uf( i ), v.vf( i ) );

		final Triangles bt = bufferMesh.triangles();
		final Triangles t = mesh.triangles();
		for ( int i = 0; i < ntriangles; i++ )
			bt.addf( t.vertex0( i ), t.vertex1( i ), t.vertex2( i ), t.nxf( i ), t.nyf( i ), t.nzf( i ) );

		return bufferMesh;
	}
}
