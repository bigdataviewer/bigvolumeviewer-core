package tpietzsch.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import tpietzsch.backend.GpuContext;
import tpietzsch.cache.PboChain.PboUploadBuffer;
import tpietzsch.cache.TextureCache.StagedTasks;
import tpietzsch.cache.TextureCache.TileFillTask;

public class ProcessFillTasks
{
	public static void sequential(
			final TextureCache textureCache,
			final PboChain pboChain,
			final GpuContext context,
			final Collection< ? extends FillTask > tasks ) throws InterruptedException
	{
		final StagedTasks stagedTasks = textureCache.stage( tasks );
		pboChain.init( stagedTasks );
		final int numTasks = stagedTasks.tasks.size();
		for ( int i = 0; i < numTasks; i++ )
		{
			pboChain.tryActivate( context );
			final TileFillTask task = pboChain.nextTask();
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
		final StagedTasks stagedTasks = textureCache.stage( tasks );
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
								final TileFillTask task = pboChain.nextTask();
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
