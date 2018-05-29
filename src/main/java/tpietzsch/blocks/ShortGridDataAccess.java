package tpietzsch.blocks;

import net.imglib2.RandomAccess;
import net.imglib2.img.basictypeaccess.array.AbstractShortArray;
import net.imglib2.img.cell.Cell;

public class ShortGridDataAccess implements GridDataAccess< short[] >
{
	private final RandomAccess< ? extends Cell< ? extends AbstractShortArray< ? > > > cellAccess;

	public ShortGridDataAccess( final RandomAccess< ? extends Cell< ? extends AbstractShortArray< ? > > > cellAccess )
	{
		this.cellAccess = cellAccess;
	}

	@Override
	public void fwd( final int d )
	{
		cellAccess.fwd( d );
	}

	@Override
	public void setPosition( final int position, final int d )
	{
		cellAccess.setPosition( position, d );
	}

	@Override
	public void setPosition( final int[] position )
	{
		cellAccess.setPosition( position );
	}

	@Override
	public int[] getPosition()
	{
		return new int[] { cellAccess.getIntPosition( 0 ), cellAccess.getIntPosition( 1 ), cellAccess.getIntPosition( 2 ) };
	}

	@Override
	public short[] get()
	{
		return cellAccess.get().getData().getCurrentStorageArray();
	}
}
