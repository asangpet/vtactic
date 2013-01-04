package ak.vtactic.model;

import java.util.Date;

import org.bson.types.ObjectId;

public class UtilizationInfo {
	public ObjectId _id;
	
	public String host;
	public String plugin;
	public Date time;
	public Double[] values;
}
