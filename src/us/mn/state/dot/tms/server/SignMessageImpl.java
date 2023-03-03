/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2000-2023  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package us.mn.state.dot.tms.server;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import us.mn.state.dot.sched.DebugLog;
import us.mn.state.dot.tms.DMSHelper;
import us.mn.state.dot.tms.DmsMsgPriority;
import us.mn.state.dot.tms.SignConfig;
import us.mn.state.dot.tms.SignConfigHelper;
import us.mn.state.dot.tms.SignMessage;
import us.mn.state.dot.tms.TMSException;
import static us.mn.state.dot.tms.server.XmlWriter.createAttribute;

/**
 * A sign message represents a message which can be displayed on a dynamic
 * message sign (DMS).  All values in these messages are *immutable* -- if any
 * changes are needed, a new sign message must be created.
 *
 * @author Douglas Lau
 */
public class SignMessageImpl extends BaseObjectImpl implements SignMessage {

	/** Sign msg debug log */
	static private final DebugLog MSG_LOG = new DebugLog("sign_msg");

	/** Last allocated system message ID */
	static private int last_id = 0;

	/** Create a unique sign message name */
	static private synchronized String createUniqueName() {
		String n = createNextName();
		while (namespace.lookupObject(SONAR_TYPE, n) != null)
			n = createNextName();
		return n;
	}

	/** Create the next system message name */
	static private String createNextName() {
		last_id++;
		// Check if the ID has rolled over to negative numbers
		if (last_id < 0)
			last_id = 0;
		return "system_" + last_id;
	}

	/** Load all the sign messages */
	static protected void loadAll() throws TMSException {
		namespace.registerType(SONAR_TYPE, SignMessageImpl.class);
		store.query("SELECT name, sign_config, incident, multi, " +
			"msg_owner, flash_beacon, msg_priority, source, " +
			"duration FROM iris." + SONAR_TYPE + ";",
			new ResultFactory()
		{
			public void create(ResultSet row) throws Exception {
				namespace.addObject(new SignMessageImpl(row));
			}
		});
	}

	/** Get a mapping of the columns */
	@Override
	public Map<String, Object> getColumns() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("name", name);
		map.put("sign_config", sign_config);
		map.put("incident", incident);
		map.put("multi", multi);
		map.put("msg_owner", msg_owner);
		map.put("flash_beacon", flash_beacon);
		map.put("msg_priority", msg_priority);
		map.put("source", source);
		map.put("duration", duration);
		return map;
	}

	/** Get the database table name */
	@Override
	public String getTable() {
		return "iris." + SONAR_TYPE;
	}

	/** Get the SONAR type name */
	@Override
	public String getTypeName() {
		return SONAR_TYPE;
	}

	/** Create a new sign message (by SONAR clients) */
	public SignMessageImpl(String n) {
		super(n);
		logMsg("created (client)");
	}

	/** Create a sign message */
	private SignMessageImpl(ResultSet row) throws SQLException {
		this(row.getString(1),           // name
		     row.getString(2),           // sign_config
		     row.getString(3),           // incident
		     row.getString(4),           // multi
		     row.getString(5),           // msg_owner
		     row.getBoolean(6),          // flash_beacon
		     row.getInt(7),              // msg_priority
		     row.getInt(8),              // source
		     (Integer) row.getObject(9)  // duration
		);
	}

	/** Create a sign message */
	private SignMessageImpl(String n, String sc, String inc, String m,
		String o, boolean fb, int mp, int s, Integer d)
	{
		super(n);
		sign_config = SignConfigHelper.lookup(sc);
		incident = inc;
		multi = m;
		msg_owner = o;
		flash_beacon = fb;
		msg_priority = mp;
		source = s;
		duration = d;
	}

	/** Create a new sign message (by IRIS) */
	public SignMessageImpl(SignConfig sc, String inc, String m, String o,
		boolean fb, DmsMsgPriority mp, int s, Integer d)
	{
		super(createUniqueName());
		sign_config = sc;
		incident = inc;
		multi = m;
		msg_owner = o;
		flash_beacon = fb;
		msg_priority = mp.ordinal();
		source = s;
		duration = d;
		logMsg("created (server)");
	}

	/** Log a message */
	void logMsg(String msg) {
		if (MSG_LOG.isOpen())
			MSG_LOG.log(getName() + ": " + msg);
	}

	/** Sign configuration */
	private SignConfig sign_config;

	/** Get the sign configuration */
	@Override
	public SignConfig getSignConfig() {
		return sign_config;
	}

	/** Associated incident (original name) */
	private String incident;

	/** Get the associated incident */
	@Override
	public String getIncident() {
		return incident;
	}

	/** Message MULTI string, contains message text for all pages */
	private String multi;

	/** Get the message MULTI string.
	 * @return Message text in MULTI markup.
	 * @see us.mn.state.dot.tms.utils.MultiString */
	@Override
	public String getMulti() {
		return multi;
	}

	/** Message owner */
	private String msg_owner;

	/** Get the message owner.
	 *
	 * It contains 3 parts, separated by semicolons, for example
	 * "IRIS; operator+schedule; john.smith"
	 *  1. System ("IRIS")
	 *  2. Sources ("operator+schedule")
	 *  3. Name: user or action plan ("john.smith")
	 *
	 * @return Message owner
	 * @see us.mn.state.dot.tms.SignMsgSource
	 */
	@Override
	public String getMsgOwner() {
		return msg_owner;
	}

	/** Flash beacon flag */
	private boolean flash_beacon;

	/** Get flash beacon flag */
	@Override
	public boolean getFlashBeacon() {
		return flash_beacon;
	}

	/** Message priority */
	private int msg_priority;

	/** Get the message priority.
	 * @return Priority ranging from 1 (low) to 255 (high).
	 * @see us.mn.state.dot.tms.DmsMsgPriority */
	@Override
	public int getMsgPriority() {
		return msg_priority;
	}

	/** Sign message source */
	private int source;

	/** Get the sign message source value.
	 * @return Sign message source.
	 * @see us.mn.state.dot.tms.SignMsgSource */
	@Override
	public int getSource() {
		return source;
	}

	/** Duration of message (minutes) */
	private Integer duration;

	/** Get the message duration.
	 * @return Duration in minutes; null means indefinite. */
	@Override
	public Integer getDuration() {
		return duration;
	}

	/** Write the SignMessage object as xml */
	public void writeXml(Writer w, DMSImpl dms) throws IOException {
		w.write("<sign_message");
		w.write(createAttribute("dms", dms.getName()));
		w.write(createAttribute("status", DMSHelper.getAllStyles(dms)));
		w.write(createAttribute("flash_beacon", flash_beacon));
		w.write(createAttribute("run_priority", msg_priority));
		w.write(createAttribute("act_priority", msg_priority));
		w.write(createAttribute("source", getSource()));
		w.write(createAttribute("duration", getDuration()));
		w.write(createAttribute("incident", getIncident()));
		w.write(createAttribute("multi", multi));
		w.write(createAttribute("bitmaps", "")); // encode from multi?
		w.write("/>\n");
	}
}
