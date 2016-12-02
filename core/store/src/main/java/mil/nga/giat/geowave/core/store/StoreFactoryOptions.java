package mil.nga.giat.geowave.core.store;

import com.beust.jcommander.Parameter;

import mil.nga.giat.geowave.core.store.operations.remote.options.DataStorePluginOptions;

/**
 * This interface doesn't actually do anything, is just used for tracking during
 * development.
 */
abstract public class StoreFactoryOptions
{

	public final static String GEOWAVE_NAMESPACE_OPTION = "gwNamespace";

	@Parameter(names = "--" + GEOWAVE_NAMESPACE_OPTION, description = "The geowave namespace (optional; default is no namespace)")
	private String geowaveNamespace;

	public String getGeowaveNamespace() {
		return geowaveNamespace;
	}

	public void setGeowaveNamespace(
			final String geowaveNamespace ) {
		this.geowaveNamespace = geowaveNamespace;
	}

	public abstract StoreFactoryFamilySpi getStoreFactory();

	public DataStorePluginOptions createPluginOptions() {
		return new DataStorePluginOptions(
				this);
	}
}
