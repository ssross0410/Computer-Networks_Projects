import java.io.*;
import java.util.*;

public class NeighborInfo {	

	private HashMap<String, Integer> neighborDV;
	private String neighborAddress;

	public NeighborInfo(String s) {
		neighborDV = new HashMap<String, Integer>();
		neighborAddress = s;
	}

	public void updateNeighborDV(String dest, int cost) {
		neighborDV.put(dest, cost);
	}

	public int getCost(String dest) {
		int cost = 0;
		Iterator<Map.Entry<String, Integer>> iterator = neighborDV.entrySet().iterator();
		while(iterator.hasNext()) {
	   		Map.Entry<String, Integer> entry = iterator.next();
	   		if(dest.equals(entry.getKey())) {
	   			cost = entry.getValue();
	   		}
	   	}
	   	return cost;
	}

	public int getLinkToDestCost(String dest) {
		Iterator<Map.Entry<String, Integer>> iterator = neighborDV.entrySet().iterator();
		while(iterator.hasNext()) {
	   		Map.Entry<String, Integer> entry = iterator.next();
	   		if(dest.equals(entry.getKey())) {
	   			return entry.getValue();
	   		}	
	   	}
	   	return -50000;		
	}

	public String getNeighbor() {
		return neighborAddress;
	}

	public HashMap<String, Integer> getNeighborDV() {
		return neighborDV;
	}
}	