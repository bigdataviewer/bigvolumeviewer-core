/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
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
package bvv.examples;

import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import org.joml.Matrix4f;
import tpietzsch.example2.VolumeViewerPanel;
import tpietzsch.scene.mesh.MeshPlayground;
import tpietzsch.scene.mesh.StupidMesh;

public class ExampleMesh
{
	public static void main( final String[] args )
	{
		final Bvv bvv = BvvFunctions.show(
				Bvv.options()
						.maxAllowedStepInVoxels( 0 )
						.renderWidth( 1024 )
						.renderHeight( 1024 )
						.preferredSize( 512, 512 ) );
		final VolumeViewerPanel viewer = bvv.getBvvHandle().getViewerPanel();
		AffineTransform3D vt = new AffineTransform3D();
		vt.set( 1, 0, 0, 243,
				0, 1, 0, 252,
				0, 0, 1, 0 );
		viewer.state().setViewerTransform( vt );

		final StupidMesh mesh = new StupidMesh( MeshPlayground.load() );
		viewer.setRenderScene( ( gl, data ) -> {
			final Matrix4f meshtransform = new Matrix4f().scale( 10 );
			final Matrix4f pvm = data.getPv().mul( meshtransform, new Matrix4f() );
			final Matrix4f vm = data.getCamview().mul( meshtransform, new Matrix4f() );
			mesh.draw( gl, pvm, vm );
		} );
		viewer.requestRepaint();
	}
}
