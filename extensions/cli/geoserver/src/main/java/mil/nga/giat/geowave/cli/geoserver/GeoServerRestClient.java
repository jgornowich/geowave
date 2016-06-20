package mil.nga.giat.geowave.cli.geoserver;

import java.io.FileWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class GeoServerRestClient
{
	private final static Logger logger = Logger.getLogger(GeoServerRestClient.class);
	private final static int defaultIndentation = 2;

	private GeoServerConfig config;
	private WebTarget webTarget = null;

	public GeoServerRestClient(
			GeoServerConfig config ) {
		this.config = config;
		logger.setLevel(Level.DEBUG);
	}

	public GeoServerConfig getConfig() {
		return config;
	}

	private WebTarget getWebTarget() {
		if (webTarget == null) {
			final Client client = ClientBuilder.newClient().register(
					HttpAuthenticationFeature.basic(
							config.getUser(),
							config.getPass()));

			webTarget = client.target(config.getUrl());
		}

		return webTarget;
	}

	// Workspaces
	public Response getWorkspaces() {
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces.json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			// get the workspace names
			final JSONArray workspaceArray = getArrayEntryNames(
					JSONObject.fromObject(resp.readEntity(String.class)),
					"workspaces",
					"workspace");

			final JSONObject workspacesObj = new JSONObject();
			workspacesObj.put(
					"workspaces",
					workspaceArray);

			return Response.ok(
					workspacesObj.toString(defaultIndentation)).build();
		}

		return resp;
	}

	public Response addWorkspace(
			final String workspace ) {
		return getWebTarget().path(
				"geoserver/rest/workspaces").request().post(
				Entity.entity(
						"{'workspace':{'name':'" + workspace + "'}}",
						MediaType.APPLICATION_JSON));
	}

	public Response deleteWorkspace(
			final String workspace ) {
		return getWebTarget().path(
				"geoserver/rest/workspaces/" + workspace).queryParam(
				"recurse",
				"true").request().delete();
	}

	// Datastores
	public Response getDatastore(
			final String workspaceName,
			String datastoreName ) {
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/datastores/" + datastoreName + ".json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			JSONObject datastore = JSONObject.fromObject(resp.readEntity(String.class));

			if (datastore != null) {
				return Response.ok(
						datastore.toString(defaultIndentation)).build();
			}
		}

		return resp;
	}

	public Response getDatastores(
			String workspaceName ) {
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/datastores.json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			// get the datastore names
			final JSONArray datastoreArray = getArrayEntryNames(
					JSONObject.fromObject(resp.readEntity(String.class)),
					"dataStores",
					"dataStore");

			final JSONObject dsObj = new JSONObject();
			dsObj.put(
					"dataStores",
					datastoreArray);

			return Response.ok(
					dsObj.toString(defaultIndentation)).build();
		}

		return resp;
	}

	public Response addDatastore(
			String workspaceName,
			String datastoreName,
			String geowaveStoreType,
			Map<String, String> geowaveStoreConfig ) {
		String lockMgmt = "memory";
		String authMgmtPrvdr = "empty";
		String authDataUrl = "";
		String queryIndexStrategy = "Best Match";

		final String dataStoreJson = createDatastoreJson(
				geowaveStoreType,
				geowaveStoreConfig,
				datastoreName,
				lockMgmt,
				authMgmtPrvdr,
				authDataUrl,
				queryIndexStrategy,
				true);

		// create a new geoserver style
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/datastores").request().post(
				Entity.entity(
						dataStoreJson,
						MediaType.APPLICATION_JSON));

		if (resp.getStatus() == Status.CREATED.getStatusCode()) {
			return Response.ok().build();
		}

		return resp;
	}

	public Response deleteDatastore(
			String workspaceName,
			String datastoreName ) {
		return getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/datastores/" + datastoreName).queryParam(
				"recurse",
				"true").request().delete();
	}

	// Layers
	public Response getLayer(
			final String layerName ) {
		final Response resp = getWebTarget().path(
				"geoserver/rest/layers/" + layerName + ".json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			JSONObject layer = JSONObject.fromObject(resp.readEntity(String.class));

			if (layer != null) {
				return Response.ok(
						layer.toString(defaultIndentation)).build();
			}
		}

		return resp;
	}

	/**
	 * Get list of layers from geoserver
	 * 
	 * @param workspaceName
	 *            : if null, don't filter on workspace
	 * @param datastoreName
	 *            : if null, don't filter on datastore
	 * @param geowaveOnly
	 *            : if true, only return geowave layers
	 * @return
	 */
	public Response getLayers(
			String workspaceName,
			String datastoreName,
			boolean geowaveOnly ) {
		boolean wsFilter = (workspaceName != null && !workspaceName.isEmpty());
		boolean dsFilter = (datastoreName != null && !datastoreName.isEmpty());

		final Response resp = getWebTarget().path(
				"geoserver/rest/layers.json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			// get the datastore names
			final JSONArray layerArray = getArrayEntryNames(
					JSONObject.fromObject(resp.readEntity(String.class)),
					"layers",
					"layer");

			// holder for simple layer info (when geowaveOnly = false)
			final JSONArray layerInfoArray = new JSONArray();

			final Map<String, List<String>> namespaceLayersMap = new HashMap<String, List<String>>();
			final Pattern p = Pattern.compile("workspaces/(.*?)/datastores/(.*?)/");
			for (int i = 0; i < layerArray.size(); i++) {
				boolean include = !geowaveOnly && !wsFilter && !dsFilter; // no filtering of any kind

				if (include) { // just grab it...
					layerInfoArray.add(layerArray.getJSONObject(i));
					continue; // and move on
				}

				// at this point, we are filtering somehow. get some more info
				// about the layer
				final String name = layerArray.getJSONObject(
						i).getString(
						"name");

				final String layer = (String) getLayer(
						name).getEntity();

				// get the workspace and name for each datastore
				String ws = null;
				String ds = null;

				final Matcher m = p.matcher(layer);

				if (m.find()) {
					ws = m.group(1);
					ds = m.group(2);
				}

				// filter on datastore?
				if (!dsFilter || (ds != null && ds.equals(datastoreName))) {

					// filter on workspace?
					if (!wsFilter || (ws != null && ws.equals(workspaceName))) {
						final JSONObject datastore = JSONObject.fromObject(
								getDatastore(
										ds,
										ws).getEntity()).getJSONObject(
								"dataStore");

						// only process GeoWave layers
						if (geowaveOnly) {
							if (datastore != null && datastore.containsKey("type") && datastore.getString(
									"type").startsWith(
									"GeoWave Datastore")) {

								JSONArray entryArray = null;
								if (datastore.get("connectionParameters") instanceof JSONObject) {
									entryArray = datastore.getJSONObject(
											"connectionParameters").getJSONArray(
											"entry");
								}
								else if (datastore.get("connectionParameters") instanceof JSONArray) {
									entryArray = datastore.getJSONArray(
											"connectionParameters").getJSONObject(
											0).getJSONArray(
											"entry");
								}

								if (entryArray == null) {
									logger.error("entry Array is null - didn't find a connectionParameters datastore object that was a JSONObject or JSONArray");
								}
								else {
									// group layers by namespace
									for (int j = 0; j < entryArray.size(); j++) {
										final JSONObject entry = entryArray.getJSONObject(j);
										final String key = entry.getString("@key");
										final String value = entry.getString("$");

										if (key.startsWith("gwNamespace")) {
											if (namespaceLayersMap.containsKey(value)) {
												namespaceLayersMap.get(
														value).add(
														name);
											}
											else {
												final ArrayList<String> layers = new ArrayList<String>();
												layers.add(name);
												namespaceLayersMap.put(
														value,
														layers);
											}
											break;
										}
									}
								}
							}
						}
						else { // just get all the layers from this store
							layerInfoArray.add(layerArray.getJSONObject(i));
						}
					}
				}
			}

			// Handle geowaveOnly response
			if (geowaveOnly) {
				// create the json object with layers sorted by namespace
				final JSONArray layersArray = new JSONArray();
				for (final Map.Entry<String, List<String>> kvp : namespaceLayersMap.entrySet()) {
					final JSONArray layers = new JSONArray();

					for (int i = 0; i < kvp.getValue().size(); i++) {
						final JSONObject layerObj = new JSONObject();
						layerObj.put(
								"name",
								kvp.getValue().get(
										i));
						layers.add(layerObj);
					}

					final JSONObject layersObj = new JSONObject();
					layersObj.put(
							"namespace",
							kvp.getKey());
					layersObj.put(
							"layers",
							layers);

					layersArray.add(layersObj);
				}

				final JSONObject layersObj = new JSONObject();
				layersObj.put(
						"layers",
						layersArray);

				return Response.ok(
						layersObj.toString(defaultIndentation)).build();
			}
			else {
				final JSONObject layersObj = new JSONObject();
				layersObj.put(
						"layers",
						layerInfoArray);

				return Response.ok(
						layersObj.toString(defaultIndentation)).build();
			}
		}

		return resp;
	}

	public Response addLayer(
			final String workspaceName,
			final String datastoreName,
			final String styleName,
			final String layerName ) {
		Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/datastores/" + datastoreName + "/featuretypes").request().post(
				Entity.entity(
						"{'featureType':{'name':'" + layerName + "'}}",
						MediaType.APPLICATION_JSON));

		if (resp.getStatus() != Status.CREATED.getStatusCode()) {
			return resp;
		}

		resp = getWebTarget().path(
				"geoserver/rest/layers/" + layerName).request().put(
				Entity.entity(
						"{'layer':{'defaultStyle':{'name':'" + styleName + "'}}}",
						MediaType.APPLICATION_JSON));

		return resp;
	}

	public Response deleteLayer(
			final String layerName ) {
		return getWebTarget().path(
				"geoserver/rest/layers/" + layerName).request().delete();
	}

	// Coverage Stores
	public Response getCoverageStore(
			final String workspaceName,
			String coverageName ) {
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores/" + coverageName + ".json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			JSONObject cvgstore = JSONObject.fromObject(resp.readEntity(String.class));

			if (cvgstore != null) {
				return Response.ok(
						cvgstore.toString(defaultIndentation)).build();
			}
		}

		return resp;
	}

	public Response getCoverageStores(
			String workspaceName ) {
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores.json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			// get the datastore names
			final JSONArray coveragesArray = getArrayEntryNames(
					JSONObject.fromObject(resp.readEntity(String.class)),
					"coverageStores",
					"coverageStore");

			final JSONObject dsObj = new JSONObject();
			dsObj.put(
					"coverageStores",
					coveragesArray);

			return Response.ok(
					dsObj.toString(defaultIndentation)).build();
		}

		return resp;
	}

	public Response addCoverageStore(
			Map<String, String> geowaveStoreConfig ) {
		String workspaceName = geowaveStoreConfig.get(GeoServerConfig.GEOSERVER_WORKSPACE);

		final String cvgStoreXml = createCoverageXml(geowaveStoreConfig);

		System.out.println("Add coverage store - xml params:\n" + cvgStoreXml);

		// create a new geoserver style
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores").request().post(
				Entity.entity(
						cvgStoreXml,
						MediaType.APPLICATION_XML));

		if (resp.getStatus() == Status.CREATED.getStatusCode()) {
			return Response.ok().build();
		}

		return resp;
	}

	public Response deleteCoverageStore(
			String workspaceName,
			String cvgstoreName ) {
		return getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores/" + cvgstoreName).queryParam(
				"recurse",
				"true").request().delete();
	}

	// Coverages (raster layers)
	public Response getCoverages(
			String workspaceName,
			String cvsstoreName ) {
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores/" + cvsstoreName + "/coverages.json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			// get the datastore names
			final JSONArray coveragesArray = getArrayEntryNames(
					JSONObject.fromObject(resp.readEntity(String.class)),
					"coverages",
					"coverage");

			final JSONObject dsObj = new JSONObject();
			dsObj.put(
					"coverages",
					coveragesArray);

			return Response.ok(
					dsObj.toString(defaultIndentation)).build();
		}

		return resp;
	}

	public Response getCoverage(
			final String workspaceName,
			String cvgStoreName,
			String coverageName ) {
		final Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores/" + cvgStoreName + "/coverages/" + coverageName + ".json").request().get();

		if (resp.getStatus() == Status.OK.getStatusCode()) {
			resp.bufferEntity();

			JSONObject cvg = JSONObject.fromObject(resp.readEntity(String.class));

			if (cvg != null) {
				return Response.ok(
						cvg.toString(defaultIndentation)).build();
			}
		}

		return resp;
	}
	
	public Response addCoverage(
			final String workspaceName,
			final String cvgStoreName,
			final String coverageName ) {
		String jsonString = "{'coverage':"
				+ "{'name':'" + coverageName + "',"
				+ "'nativeName':'" + coverageName + "'}}";
		logger.debug("Posting JSON: " + jsonString + " to " + workspaceName + "/" + cvgStoreName);
		
		Response resp = getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores/" + cvgStoreName + "/coverages").request().post(
				Entity.entity(
						jsonString,
						MediaType.APPLICATION_JSON));

		if (resp.getStatus() != Status.CREATED.getStatusCode()) {
			return resp;
		}

		return resp;
	}

	public Response deleteCoverage(
			String workspaceName,
			String cvgstoreName,
			String coverageName ) {
		return getWebTarget().path(
				"geoserver/rest/workspaces/" + workspaceName + "/coveragestores/" + cvgstoreName + "/coverages/" + coverageName).queryParam(
				"recurse",
				"true").request().delete();
	}

	// Internal methods
	protected String createFeatureTypeJson(
			final String featureTypeName ) {
		final JSONObject featTypeJson = new JSONObject();

		featTypeJson.put(
				"name",
				featureTypeName);

		final JSONObject jsonObj = new JSONObject();
		jsonObj.put(
				"featureType",
				featTypeJson);

		return jsonObj.toString();
	}

	protected JSONArray getArrayEntryNames(
			JSONObject jsonObj,
			final String firstKey,
			final String secondKey ) {
		// get the top level object/array
		if (jsonObj.get(firstKey) instanceof JSONObject) {
			jsonObj = jsonObj.getJSONObject(firstKey);
		}
		else if (jsonObj.get(firstKey) instanceof JSONArray) {
			final JSONArray tempArray = jsonObj.getJSONArray(firstKey);
			if (tempArray.size() > 0) {
				if (tempArray.get(0) instanceof JSONObject) {
					jsonObj = tempArray.getJSONObject(0);
				}
				else {
					// empty list!
					return new JSONArray();
				}
			}
		}

		// get the sub level object/array
		final JSONArray entryArray = new JSONArray();
		if (jsonObj.get(secondKey) instanceof JSONObject) {
			final JSONObject entry = new JSONObject();
			entry.put(
					"name",
					jsonObj.getJSONObject(
							secondKey).getString(
							"name"));
			entryArray.add(entry);
		}
		else if (jsonObj.get(secondKey) instanceof JSONArray) {
			final JSONArray entries = jsonObj.getJSONArray(secondKey);
			for (int i = 0; i < entries.size(); i++) {
				final JSONObject entry = new JSONObject();
				entry.put(
						"name",
						entries.getJSONObject(
								i).getString(
								"name"));
				entryArray.add(entry);
			}
		}
		return entryArray;
	}

	protected String createDatastoreJson(
			final String geowaveStoreType,
			final Map<String, String> geowaveStoreConfig,
			final String name,
			final String lockMgmt,
			final String authMgmtProvider,
			final String authDataUrl,
			final String queryIndexStrategy,
			final boolean enabled ) {
		final JSONObject dataStore = new JSONObject();
		dataStore.put(
				"name",
				name);
		dataStore.put(
				"type",
				GeoServerConfig.DISPLAY_NAME_PREFIX + geowaveStoreType);
		dataStore.put(
				"enabled",
				Boolean.toString(enabled));

		final JSONObject connParams = new JSONObject();

		if (geowaveStoreConfig != null) {
			for (final Entry<String, String> e : geowaveStoreConfig.entrySet()) {
				connParams.put(
						e.getKey(),
						e.getValue());
			}
		}
		connParams.put(
				"Lock Management",
				lockMgmt);

		connParams.put(
				GeoServerConfig.QUERY_INDEX_STRATEGY_KEY,
				queryIndexStrategy);

		connParams.put(
				"Authorization Management Provider",
				authMgmtProvider);
		if (!authMgmtProvider.equals("empty")) {
			connParams.put(
					"Authorization Data URL",
					authDataUrl);
		}

		dataStore.put(
				"connectionParameters",
				connParams);

		final JSONObject jsonObj = new JSONObject();
		jsonObj.put(
				"dataStore",
				dataStore);

		return jsonObj.toString();
	}

	private String createCoverageXml(
			Map<String, String> geowaveStoreConfig ) {
		String coverageXml = null;

		String workspace = geowaveStoreConfig.get(GeoServerConfig.GEOSERVER_WORKSPACE);
		String cvgstoreName = geowaveStoreConfig.get("geoserver.coverageStore");
//		String storeConfigUrl = geowaveStoreConfig.get(GeoServerConfig.GS_STORE_URL);
//		String storeConfigPath = geowaveStoreConfig.get(GeoServerConfig.GS_STORE_PATH);

		try {
			// create the post XML
			Document xmlDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

			Element rootEl = xmlDoc.createElement("coverageStore");
			xmlDoc.appendChild(rootEl);

			Element nameEl = xmlDoc.createElement("name");
			nameEl.appendChild(xmlDoc.createTextNode(cvgstoreName));
			rootEl.appendChild(nameEl);

			Element wsEl = xmlDoc.createElement("workspace");
			wsEl.appendChild(xmlDoc.createTextNode(workspace));
			rootEl.appendChild(wsEl);

			Element typeEl = xmlDoc.createElement("type");
			typeEl.appendChild(xmlDoc.createTextNode("GeoWaveRasterFormat"));
			rootEl.appendChild(typeEl);

			Element enabledEl = xmlDoc.createElement("enabled");
			enabledEl.appendChild(xmlDoc.createTextNode("true"));
			rootEl.appendChild(enabledEl);

			Element configEl = xmlDoc.createElement("configure");
			configEl.appendChild(xmlDoc.createTextNode("all"));
			rootEl.appendChild(configEl);

			// Method using custom URL & handler:
			String storeConfigUrl = createParamUrl(geowaveStoreConfig);

			Element urlEl = xmlDoc.createElement("url");
			urlEl.appendChild(xmlDoc.createTextNode(storeConfigUrl));
			rootEl.appendChild(urlEl);

			/* 
			// Retrieve store config
			String user = geowaveStoreConfig.get("user");
			String pass = geowaveStoreConfig.get("password");
			String zookeeper = geowaveStoreConfig.get("zookeeper");
			String instance = geowaveStoreConfig.get("instance");
			
			// Write the temp XML file for the store config
			writeConfigXml(
					storeConfigPath,
					user,
					pass,
					zookeeper,
					instance,
					cvgstoreName);
			*/

			// use a transformer to create the xml string for the rest call
			Transformer xformer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(
					xmlDoc);
			StreamResult result = new StreamResult(
					new StringWriter());

			xformer.transform(
					source,
					result);
			
			coverageXml = result.getWriter().toString();
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return coverageXml;
	}

	private String createParamUrl(
			Map<String, String> geowaveStoreConfig ) {
		// Retrieve store config
		String user = geowaveStoreConfig.get("user");
		String pass = geowaveStoreConfig.get("password");
		String zookeeper = geowaveStoreConfig.get("zookeeper");
		String instance = geowaveStoreConfig.get("instance");
		String gwNamespace = geowaveStoreConfig.get("geoserver.coverageStore");
		
		// Create the custom geowave url w/ params
		StringBuffer buf = new StringBuffer();
//		buf.append(GeoWaveUrlStreamHandler.GW_PROTOCOL);
//		buf.append(":");
		buf.append("user=");
		buf.append(user);
		buf.append(";password=");
		buf.append(pass);
		buf.append(";zookeeper=");
		buf.append(zookeeper);
		buf.append(";instance=");
		buf.append(instance);
		buf.append(";gwNamespace=");
		buf.append(gwNamespace);
		
		return buf.toString();
	}

	private void writeConfigXml(
			String storeConfigPath,
			String user,
			String pass,
			String zookeeper,
			String instance,
			String cvgstoreName ) {
		try {
			// create the post XML
			Document xmlDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

			Element configEl = xmlDoc.createElement("config");
			xmlDoc.appendChild(configEl);

			Element userEl = xmlDoc.createElement("user");
			userEl.appendChild(xmlDoc.createTextNode(user));
			configEl.appendChild(userEl);

			Element passEl = xmlDoc.createElement("password");
			passEl.appendChild(xmlDoc.createTextNode(pass));
			configEl.appendChild(passEl);

			Element zkEl = xmlDoc.createElement("zookeeper");
			zkEl.appendChild(xmlDoc.createTextNode(zookeeper));
			configEl.appendChild(zkEl);

			Element instEl = xmlDoc.createElement("instance");
			instEl.appendChild(xmlDoc.createTextNode(instance));
			configEl.appendChild(instEl);

			Element gwnsEl = xmlDoc.createElement("gwNamespace");
			gwnsEl.appendChild(xmlDoc.createTextNode(cvgstoreName));
			configEl.appendChild(gwnsEl);
			
			Transformer xformer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(
					xmlDoc);
			
			String xmlFile = storeConfigPath + "/gwraster.xml";			
			FileWriter xmlWriter = new FileWriter(xmlFile);
			
			StreamResult result = new StreamResult(xmlWriter);

			xformer.transform(
					source,
					result);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Example use of geoserver rest client
	public static void main(
			final String[] args ) {
		// create the client
		GeoServerConfig config = new GeoServerConfig();
		GeoServerRestClient geoserverClient = new GeoServerRestClient(
				config);

		// test getWorkspaces
		// Response getWorkspacesResponse = geoserverClient.getWorkspaces();
		//
		// if (getWorkspacesResponse.getStatus() == Status.OK.getStatusCode()) {
		// System.out.println("\nList of GeoServer workspaces:");
		//
		// JSONObject jsonResponse =
		// JSONObject.fromObject(getWorkspacesResponse.getEntity());
		//
		// final JSONArray workspaces = jsonResponse.getJSONArray("workspaces");
		// for (int i = 0; i < workspaces.size(); i++) {
		// String wsName = workspaces.getJSONObject(
		// i).getString(
		// "name");
		// System.out.println("  > " + wsName);
		// }
		//
		// System.out.println("---\n");
		// }
		// else {
		// System.err.println("Error getting GeoServer workspace list; code = "
		// + getWorkspacesResponse.getStatus());
		// }
		//
		// // test addWorkspace
		// Response addWorkspaceResponse =
		// geoserverClient.addWorkspace("delete-me-ws");
		// if (addWorkspaceResponse.getStatus() ==
		// Status.CREATED.getStatusCode()) {
		// System.out.println("Add workspace 'delete-me-ws' to GeoServer: OK");
		// }
		// else {
		// System.err.println("Error adding workspace 'delete-me-ws' to GeoServer; code = "
		// + addWorkspaceResponse.getStatus());
		// }

		// test coverage store list
		Response listCoveragesResponse = geoserverClient.getCoverageStores("geowave");

		if (listCoveragesResponse.getStatus() == Status.OK.getStatusCode()) {
			System.out.println("\nGeoServer coverage stores list for 'geowave':");

			JSONObject jsonResponse = JSONObject.fromObject(listCoveragesResponse.getEntity());
			JSONArray datastores = jsonResponse.getJSONArray("coverageStores");
			System.out.println(datastores.toString(2));
		}
		else {
			System.err.println("Error getting GeoServer coverage stores list for 'geowave'; code = " + listCoveragesResponse.getStatus());
		}

		// test get coverage store
		Response getCvgStoreResponse = geoserverClient.getCoverageStore(
				"geowave",
				"sfdem");

		if (getCvgStoreResponse.getStatus() == Status.OK.getStatusCode()) {
			System.out.println("\nGeoServer coverage store info for 'geowave/sfdem':");

			JSONObject jsonResponse = JSONObject.fromObject(getCvgStoreResponse.getEntity());
			JSONObject datastore = jsonResponse.getJSONObject("coverageStore");
			System.out.println(datastore.toString(2));
		}
		else {
			System.err.println("Error getting GeoServer coverage store info for 'geowave/sfdem'; code = " + getCvgStoreResponse.getStatus());
		}

		// test add store
		// HashMap<String, String> geowaveStoreConfig = new HashMap<String,
		// String>();
		// geowaveStoreConfig.put(
		// "user",
		// "root");
		// geowaveStoreConfig.put(
		// "password",
		// "password");
		// geowaveStoreConfig.put(
		// "gwNamespace",
		// "ne_50m_admin_0_countries");
		// geowaveStoreConfig.put(
		// "zookeeper",
		// "localhost:2181");
		// geowaveStoreConfig.put(
		// "instance",
		// "geowave");
		//
		// Response addStoreResponse = geoserverClient.addDatastore(
		// "delete-me-ws",
		// "delete-me-ds",
		// "accumulo",
		// geowaveStoreConfig);
		//
		// if (addStoreResponse.getStatus() == Status.OK.getStatusCode() ||
		// addStoreResponse.getStatus() == Status.CREATED.getStatusCode()) {
		// System.out.println("Add store 'delete-me-ds' to workspace 'delete-me-ws' on GeoServer: OK");
		// }
		// else {
		// System.err.println("Error adding store 'delete-me-ds' to workspace 'delete-me-ws' on GeoServer; code = "
		// + addStoreResponse.getStatus());
		// }
		//
		// // test getLayer
		// Response getLayerResponse = geoserverClient.getLayer("states");
		//
		// if (getLayerResponse.getStatus() == Status.OK.getStatusCode()) {
		// System.out.println("\nGeoServer layer info for 'states':");
		//
		// JSONObject jsonResponse =
		// JSONObject.fromObject(getLayerResponse.getEntity());
		// System.out.println(jsonResponse.toString(2));
		// }
		// else {
		// System.err.println("Error getting GeoServer layer info for 'states'; code = "
		// + getLayerResponse.getStatus());
		// }

		// test list layers
		// Response listLayersResponse = geoserverClient.getLayers(
		// "topp",
		// null,
		// false);
		// if (listLayersResponse.getStatus() == Status.OK.getStatusCode()) {
		// System.out.println("\nGeoServer layer list:");
		// JSONObject listObj =
		// JSONObject.fromObject(listLayersResponse.getEntity());
		// System.out.println(listObj.toString(2));
		// }
		// else {
		// System.err.println("Error getting GeoServer layer list; code = " +
		// listLayersResponse.getStatus());
		// }

		// test add layer
		// Response addLayerResponse = geoserverClient.addLayer(
		// "delete-me-ws",
		// "delete-me-ds",
		// "polygon",
		// "ne_50m_admin_0_countries");
		//
		// if (addLayerResponse.getStatus() == Status.OK.getStatusCode()) {
		// System.out.println("\nGeoServer layer add response for 'ne_50m_admin_0_countries':");
		//
		// JSONObject jsonResponse = JSONObject.fromObject(addLayerResponse.getEntity());
		// System.out.println(jsonResponse.toString(2));
		// }
		// else {
		// System.err.println("Error adding GeoServer layer 'ne_50m_admin_0_countries'; code = " +
		// addLayerResponse.getStatus());
		// }

		// test delete layer
		// Response deleteLayerResponse =
		// geoserverClient.deleteLayer("ne_50m_admin_0_countries");
		// if (deleteLayerResponse.getStatus() == Status.OK.getStatusCode()) {
		// System.out.println("\nGeoServer layer delete response for 'ne_50m_admin_0_countries':");
		//
		// JSONObject jsonResponse =
		// JSONObject.fromObject(deleteLayerResponse.getEntity());
		// System.out.println(jsonResponse.toString(2));
		// }
		// else {
		// System.err.println("Error deleting GeoServer layer 'ne_50m_admin_0_countries'; code = "
		// + deleteLayerResponse.getStatus());
		// }

		// test delete store
		// Response deleteStoreResponse = geoserverClient.deleteDatastore(
		// "DeleteMe",
		// "kamteststore2");
		//
		// if (deleteStoreResponse.getStatus() == Status.OK.getStatusCode() ||
		// addStoreResponse.getStatus() == Status.CREATED.getStatusCode()) {
		// System.out.println("Delete store 'kamstoretest2' from workspace 'DeleteMe' on GeoServer: OK");
		// }
		// else {
		// System.err.println("Error deleting store 'kamstoretest2' from workspace 'DeleteMe' on GeoServer; code = "
		// + deleteStoreResponse.getStatus());
		// }

		// test deleteWorkspace
		// Response deleteWorkspaceResponse =
		// geoserverClient.deleteWorkspace("DeleteMe");
		// if (deleteWorkspaceResponse.getStatus() == Status.OK.getStatusCode())
		// {
		// System.out.println("Delete workspace 'DeleteMe' from GeoServer: OK");
		// }
		// else {
		// System.err.println("Error deleting workspace 'DeleteMe' from GeoServer; code = "
		// + deleteWorkspaceResponse.getStatus());
		// }
	}
}