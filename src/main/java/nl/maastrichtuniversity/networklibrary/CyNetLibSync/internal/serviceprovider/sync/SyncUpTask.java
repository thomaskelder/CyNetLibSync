package nl.maastrichtuniversity.networklibrary.CyNetLibSync.internal.serviceprovider.sync;

import java.io.IOException;

import nl.maastrichtuniversity.networklibrary.CyNetLibSync.internal.ResponseHandlers.ReturnCodeResponseHandler;
import nl.maastrichtuniversity.networklibrary.CyNetLibSync.internal.utils.CyUtils;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

public class SyncUpTask extends AbstractTask {
	
	private boolean wipeRemote;
	private String cypherURL;
	private CyNetwork currNet;
	
	public SyncUpTask(boolean wipeRemote,String cypherURL,CyNetwork currNet){
		this.wipeRemote = wipeRemote;
		this.cypherURL = cypherURL;
		this.currNet = currNet;
	}
	
	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setTitle("Synchronizing UP to the Server");
		double progress = 0.0;
		
		boolean wiped = false;
		if(wipeRemote){
			taskMonitor.setStatusMessage("wiping remote network");
			String wipeQuery = "{ \"query\" : \"MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r\",\"params\" : {}}";
			progress = 0.1;
			taskMonitor.setProgress(progress);

			System.out.println(wipeQuery);
			try {
				wiped = Request.Post(getCypherURL()).bodyString(wipeQuery, ContentType.APPLICATION_JSON).execute().handleResponse(new ReturnCodeResponseHandler());
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(wiped == wipeRemote){
			CyNetwork currNet = getCurrentNetwork();
			
			if(currNet == null){
				System.out.println("no network selected!");
				
			} else {
					
//				System.out.println("got a network");
				//TODO get all tables; check if node is in table; upload everything!
				CyTable defNodeTab = currNet.getDefaultNodeTable();
				if(defNodeTab.getColumn("neoid") == null){
					defNodeTab.createColumn("neoid", Long.class, false);
				}
				
				double steps = currNet.getNodeList().size() + currNet.getEdgeList().size();
				double stepSize = 0.9 / steps;
				taskMonitor.setStatusMessage("uploading nodes");
				for(CyNode node : currNet.getNodeList()){
					
					String params = CyUtils.convertCyAttributesToJson(node, defNodeTab);
					String cypher = "{ \"query\" : \"CREATE (n { props }) return id(n)\", \"params\" : {   \"props\" : [ "+ params +" ] } }";
					System.out.println(cypher);
					try {
						Long neoid = Request.Post(getCypherURL()).bodyString(cypher, ContentType.APPLICATION_JSON).execute().handleResponse(new CreateIdReturnResponseHandler());
						defNodeTab.getRow(node.getSUID()).set("neoid", neoid);
					} catch (ClientProtocolException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					progress = progress + stepSize;
					taskMonitor.setProgress(progress);
				}
				// */
				
//				System.out.println("uploaded nodes");
				
				CyTable defEdgeTab = currNet.getDefaultEdgeTable();
				if(defEdgeTab.getColumn("neoid") == null){
					defEdgeTab.createColumn("neoid", Long.class, false);
				}
				
				for(CyEdge edge : currNet.getEdgeList()){
					taskMonitor.setStatusMessage("uploading edges");
					String from = defNodeTab.getRow(edge.getSource().getSUID()).get(CyNetwork.NAME, String.class);
					String to = defNodeTab.getRow(edge.getTarget().getSUID()).get(CyNetwork.NAME, String.class);
					
					String rparams = CyUtils.convertCyAttributesToJson(edge, defEdgeTab);
					
					String rtype = defEdgeTab.getRow(edge.getSUID()).get(CyEdge.INTERACTION, String.class);
					
					String cypher = "{\"query\" : \"MATCH (from { name: {fname}}),(to { name: {tname}}) CREATE (from)-[r:"+rtype+" { rprops } ]->(to) return id(r)\", \"params\" : { \"fname\" : \""+from+"\", \"tname\" : \""+to+"\", \"rprops\" : "+ rparams +" }}";
//					System.out.println(cypher);
					try {
						Long neoid = Request.Post(getCypherURL()).bodyString(cypher, ContentType.APPLICATION_JSON).execute().handleResponse(new CreateIdReturnResponseHandler());
						defEdgeTab.getRow(edge.getSUID()).set("neoid", neoid);
					} catch (ClientProtocolException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					progress = progress + stepSize;
					taskMonitor.setProgress(progress);
				}
				// */
				
//				System.out.println("uploaded edges");
			}
			
		} else {
			System.out.println("could not wipe the instance! aborting syncUp");
		}
	}

	protected CyNetwork getCurrentNetwork() {
		return currNet;
	}

	protected String getCypherURL() {
		return cypherURL;
	}

	protected boolean isWipeRemote() {
		return wipeRemote;
	}

	protected void setWipeRemote(boolean wipeRemote) {
		this.wipeRemote = wipeRemote;
	}

	
}
