/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2024 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.vistools.examples;

import bdv.viewer.AbstractViewerPanel;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import bvv.vistools.Bvv;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvSource;
import com.jogamp.opengl.GL3;
import ij.IJ;
import ij.ImagePlus;
import java.util.ArrayList;
import java.util.Random;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.LinAlgHelpers;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import bvv.core.render.RenderData;
import bvv.core.VolumeViewerPanel;
import bvv.vistools.examples.scene.TexturedUnitCube;

public class ExampleBoxes
{
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

		final CubeScene scene = new CubeScene( viewer );
		viewer.setRenderScene( scene );
		final Actions actions = new Actions( new InputTriggerConfig() );
		actions.install( source.getBvvHandle().getKeybindings(), "additional" );
		actions.runnableAction( scene::addRandomCube, "add random cube", "B" );
		actions.runnableAction( scene::removeRandomCube, "remove random cube", "shift B" );

		viewer.requestRepaint();
	}

	static class CubeScene implements VolumeViewerPanel.RenderScene
	{
		private final TexturedUnitCube[] cubes = new TexturedUnitCube[] {
				new TexturedUnitCube( "imglib2.png" ),
				new TexturedUnitCube( "fiji.png" ),
				new TexturedUnitCube( "imagej2.png" ),
				new TexturedUnitCube( "scijava.png" ),
				new TexturedUnitCube( "container.jpg" )
		};

		private final AbstractViewerPanel viewer;

		public CubeScene( AbstractViewerPanel viewer )
		{
			this.viewer = viewer;
		}

		@Override
		public void render( final GL3 gl, final RenderData data )
		{
			synchronized ( cubeAndTransforms )
			{
				for ( final CubeAndTransform cubeAndTransform : cubeAndTransforms )
				{
					cubeAndTransform.cube.draw( gl, new Matrix4f( data.getPv() ).mul( cubeAndTransform.model ) );
				}
			}
		}

		static class CubeAndTransform
		{
			final TexturedUnitCube cube;
			final Matrix4f model;

			public CubeAndTransform( final TexturedUnitCube cube, final Matrix4f model )
			{
				this.cube = cube;
				this.model = model;
			}
		}

		private final ArrayList< CubeAndTransform > cubeAndTransforms = new ArrayList<>();

		private final Random random = new Random();

		void removeRandomCube()
		{
			synchronized ( cubeAndTransforms )
			{
				if ( !cubeAndTransforms.isEmpty() )
					cubeAndTransforms.remove( random.nextInt( cubeAndTransforms.size() ) );
			}
			viewer.requestRepaint();
		}

		void addRandomCube()
		{
			final AffineTransform3D sourceToWorld = new AffineTransform3D();
			final Interval interval;
			final ViewerState state = viewer.state();
			final int t = state.getCurrentTimepoint();
			final SourceAndConverter< ? > source = state.getCurrentSource();
			source.getSpimSource().getSourceTransform( t, 0, sourceToWorld );
			interval = source.getSpimSource().getSource( t, 0 );

			final double[] zero = new double[ 3 ];
			final double[] tzero = new double[ 3 ];
			for ( int d = 0; d < 3; ++d )
				zero[ d ] = interval.min( d );
			sourceToWorld.apply( zero, tzero );

			final double[] one = new double[ 3 ];
			final double[] tone = new double[ 3 ];
			final double[] size = new double[ 3 ];
			for ( int i = 0; i < 3; ++i )
			{
				for ( int d = 0; d < 3; ++d )
					one[ d ] = d == i ? interval.max( d ) + 1 : interval.min( d );
				sourceToWorld.apply( one, tone );
				LinAlgHelpers.subtract( tone, tzero, tone );
				size[ i ] = LinAlgHelpers.length( tone );
			}
			final TexturedUnitCube cube = cubes[ random.nextInt( cubes.length ) ];
			final Matrix4f model = new Matrix4f()
					.translation(
							( float ) ( tzero[ 0 ] + random.nextDouble() * size[ 0 ] ),
							( float ) ( tzero[ 1 ] + random.nextDouble() * size[ 1 ] ),
							( float ) ( tzero[ 2 ] + random.nextDouble() * size[ 1 ] ) )
					.scale(
							( float ) ( ( random.nextDouble() + 1 ) * size[ 0 ] * 0.05 ) )
					.rotate(
							( float ) ( random.nextDouble() * Math.PI ),
							new Vector3f( random.nextFloat(), random.nextFloat(), random.nextFloat() ).normalize()
					);

			synchronized ( cubeAndTransforms )
			{
				cubeAndTransforms.add( new CubeAndTransform( cube, model ) );
			}
			viewer.requestRepaint();
		}
	}
}
