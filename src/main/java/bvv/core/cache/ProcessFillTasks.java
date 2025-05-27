/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
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
package bvv.core.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import bvv.core.backend.GpuContext;
import bvv.core.cache.PboChain.PboUploadBuffer;

public class ProcessFillTasks
{
	public static void sequential(
			final TextureCache textureCache,
			final PboChain pboChain,
			final GpuContext context,
			final Collection< ? extends FillTask > tasks ) throws InterruptedException
	{
		final TextureCache.StagedTasks stagedTasks = textureCache.stage( tasks );
		pboChain.init( stagedTasks );
		final int numTasks = stagedTasks.tasks.size();
		for ( int i = 0; i < numTasks; i++ )
		{
			pboChain.tryActivate( context );
			final TextureCache.TileFillTask task = pboChain.nextTask();
			if ( task.containsData() )
			{
				final PboUploadBuffer buf = pboChain.take( task );
				task.fill( buf );
				pboChain.commit( buf );
			}
			pboChain.tryUpload( context );
		}
		pboChain.flush();
		pboChain.tryUpload( context );
	}

	public static void parallel(
			final TextureCache textureCache,
			final PboChain pboChain,
			final GpuContext context,
			final ForkJoinPool forkJoinPool,
			final Collection< ? extends FillTask > tasks ) throws InterruptedException
	{
		final TextureCache.StagedTasks stagedTasks = textureCache.stage( tasks );
		final int numTasks = stagedTasks.tasks.size();
		if ( numTasks == 0 )
			return;

		pboChain.init( stagedTasks );
		final int restoreId = context.bindTexture( textureCache );
		forkJoinPool.execute( new RecursiveAction()
		{
			@Override
			protected void compute()
			{
				final ArrayList< RecursiveAction > actions = new ArrayList<>();
				for ( int i = 0; i < numTasks; i++ )
				{
					final RecursiveAction fill = new RecursiveAction()
					{
						@Override
						protected void compute()
						{
							try
							{
								final TextureCache.TileFillTask task = pboChain.nextTask();
								if ( task.containsData() )
								{
									final PboUploadBuffer buf = pboChain.take( task );
									task.fill( buf );
									pboChain.commit( buf );
								}
							}
							catch ( final InterruptedException e )
							{
								throw new AssertionError( e );
							}
						}
					};
					fill.fork();
					actions.add( fill );
				}

				for ( final RecursiveAction action : actions )
					action.join();

				pboChain.flush();
			}
		} );

//		System.out.println( "{{ numTasks = " + numTasks + " }}" );
		pboChain.maintain( context );
		context.bindTextureId( restoreId, 3 );
	}
}
