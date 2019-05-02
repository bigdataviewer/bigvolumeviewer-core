package bvv.util;

/**
 * Something that has a {@link BvvHandle}. This includes {@link BvvSource}s, as
 * well as {@link BvvHandle}s (which return themselves with
 * {@link #getBvvHandle()}).
 * <p>
 * Having a {@link bvv.util.Bvv} is useful for adding more stuff to a BigDataViewer
 * window or panel. This is done using
 * {@code BdvFunctions.show(..., Bvv.options().addTo(myBdv))}.
 *
 * @author Tobias Pietzsch
 */
public interface Bvv
{
	public BvvHandle getBvvHandle();

	public default void close()
	{
		getBvvHandle().close();
	}

	public static BvvOptions options()
	{
		return BvvOptions.options();
	}
}
