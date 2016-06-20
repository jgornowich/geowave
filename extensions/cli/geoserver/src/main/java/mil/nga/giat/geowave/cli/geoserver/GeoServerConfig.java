package mil.nga.giat.geowave.cli.geoserver;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;

import mil.nga.giat.geowave.core.cli.operations.config.options.ConfigOptions;

public class GeoServerConfig
{
	public static final String GEOSERVER_URL = "geoserver.url";
	public static final String GEOSERVER_USER = "geoserver.user";
	public static final String GEOSERVER_PASS = "geoserver.pass";
	public static final String GEOSERVER_WORKSPACE = "geoserver.workspace";

	public static final String DEFAULT_URL = "http://localhost:8080";
	public static final String DEFAULT_USER = "admin";
	public static final String DEFAULT_PASS = "geoserver";
	public static final String DEFAULT_WORKSPACE = "geowave";

	public static final String GS_STORE_INSTANCE = "geoserver.store.instance";
	public static final String GS_STORE_ZOOKEEPER = "geoserver.store.zookeeper";
	public static final String GS_STORE_USER = "geoserver.store.user";
	public static final String GS_STORE_PASS = "geoserver.store.pass";
	public static final String GS_STORE_URL = "geoserver.store.config.url";
	public static final String GS_STORE_PATH = "geoserver.store.config.path";

	public static final String DEFAULT_STORE_INSTANCE = "geowave";
	public static final String DEFAULT_STORE_ZOOKEEPER = "localhost:2181";
	public static final String DEFAULT_STORE_USER = "root";
	public static final String DEFAULT_STORE_PASS = "password";
	public static final String DEFAULT_STORE_URL = "file:data/config/gwraster.xml";
	public static final String DEFAULT_STORE_PATH = "/usr/share/tomcat/webapps/geoserver/data_dir/data/config";

	public final static String DISPLAY_NAME_PREFIX = "GeoWave Datastore - ";
	public static final String QUERY_INDEX_STRATEGY_KEY = "Query Index Strategy";

	private String url = null;
	private String user = null;
	private String pass = null;
	private String workspace = null;
	private String storeUrl = null;
	private String storePath = null;

	private final File propFile;

	/**
	 * Properties File holds defaults; updates config if empty.
	 * 
	 * @param propFile
	 */
	public GeoServerConfig(
			File propFile ) {
		this.propFile = propFile;

		Properties gsConfig = ConfigOptions.loadProperties(
				propFile,
				null);

		boolean update = false;

		url = gsConfig.getProperty(GEOSERVER_URL);
		if (url == null) {
			url = DEFAULT_URL;
			gsConfig.setProperty(
					GEOSERVER_URL,
					url);
			update = true;
		}

		user = gsConfig.getProperty(GEOSERVER_USER);
		if (user == null) {
			user = DEFAULT_USER;
			gsConfig.setProperty(
					GEOSERVER_USER,
					user);
			update = true;
		}

		pass = gsConfig.getProperty(GEOSERVER_PASS);
		if (pass == null) {
			pass = DEFAULT_PASS;
			gsConfig.setProperty(
					GEOSERVER_PASS,
					pass);
			update = true;
		}

		workspace = gsConfig.getProperty(GEOSERVER_WORKSPACE);
		if (workspace == null) {
			workspace = DEFAULT_WORKSPACE;
			gsConfig.setProperty(
					GEOSERVER_WORKSPACE,
					workspace);
			update = true;
		}

		storeUrl = gsConfig.getProperty(GS_STORE_URL);
		if (storeUrl == null) {
			storeUrl = DEFAULT_STORE_URL;
			gsConfig.setProperty(
					GS_STORE_URL,
					storeUrl);
			update = true;
		}

		storePath = gsConfig.getProperty(GS_STORE_PATH);
		if (storePath == null) {
			storePath = DEFAULT_STORE_PATH;
			gsConfig.setProperty(
					GS_STORE_PATH,
					storeUrl);
			update = true;
		}

		if (update) {
			ConfigOptions.writeProperties(
					propFile,
					gsConfig);

			System.out.println("GeoServer Config Saved");
		}
	}

	/**
	 * Secondary no-arg constructor for direct-access testing
	 */
	public GeoServerConfig() {
		this.propFile = null;
		this.user = DEFAULT_USER;
		this.pass = DEFAULT_PASS;
		this.url = DEFAULT_URL;
		this.workspace = DEFAULT_WORKSPACE;
		this.storeUrl = DEFAULT_STORE_URL;
		this.storePath = DEFAULT_STORE_PATH;
	}

	public HashMap<String, String> loadStoreConfig(
			String storeName ) {
		Properties gsConfig;

		if (propFile != null) {
			gsConfig = ConfigOptions.loadProperties(
					propFile,
					null);
		}
		else {
			gsConfig = new Properties();
		}

		HashMap<String, String> geowaveStoreConfig = new HashMap<String, String>();

		String sUser = gsConfig.getProperty(
				GS_STORE_USER,
				DEFAULT_STORE_PASS);
		String sPassword = gsConfig.getProperty(
				GS_STORE_PASS,
				DEFAULT_STORE_USER);
		String sZookeeper = gsConfig.getProperty(
				GS_STORE_ZOOKEEPER,
				DEFAULT_STORE_ZOOKEEPER);
		String sInstance = gsConfig.getProperty(
				GS_STORE_INSTANCE,
				DEFAULT_STORE_INSTANCE);

		geowaveStoreConfig.put(
				"user",
				sUser);
		geowaveStoreConfig.put(
				"password",
				sPassword);
		geowaveStoreConfig.put(
				"gwNamespace",
				storeName);
		geowaveStoreConfig.put(
				"zookeeper",
				sZookeeper);
		geowaveStoreConfig.put(
				"instance",
				sInstance);

		return geowaveStoreConfig;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(
			String url ) {
		this.url = url;
	}

	public String getUser() {
		return user;
	}

	public void setUser(
			String user ) {
		this.user = user;
	}

	public String getPass() {
		return pass;
	}

	public void setPass(
			String pass ) {
		this.pass = pass;
	}

	public String getWorkspace() {
		return workspace;
	}

	public void setWorkspace(
			String workspace ) {
		this.workspace = workspace;
	}
	
	public File getPropFile() {
		return propFile;
	}

	public String getStoreUrl() {
		return storeUrl;
	}

	public void setStoreUrl(
			String storeUrl ) {
		this.storeUrl = storeUrl;
	}

	public String getStorePath() {
		return storePath;
	}

	public void setStorePath(
			String storePath ) {
		this.storePath = storePath;
	}
}